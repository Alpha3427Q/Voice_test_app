package com.projectalice.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.projectalice.ACTION_START_OVERLAY
import com.projectalice.ACTION_STOP_ENGINE
import com.projectalice.ENGINE_NOTIFICATION_CHANNEL_ID
import com.projectalice.ENGINE_NOTIFICATION_ID
import com.projectalice.R
import com.projectalice.data.EngineRepository
import com.projectalice.data.SettingsRepository
import com.projectalice.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AliceEngineService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var microphoneBouncer: MicrophoneBouncer
    private val assistantClient = AssistantClient()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isSttActive = false
    private var isShuttingDown = false

    private var partialTranscript: String = ""
    private var finalTranscript: String = ""
    private var lastSpeechSignalAtMs: Long = 0L

    private var silenceWatcherJob: Job? = null
    private var postSpeechDelayJob: Job? = null
    private var settingsCollectionJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        wakeWordEngine = PorcupineWakeWordEngine(applicationContext)
        microphoneBouncer = MicrophoneBouncer(applicationContext) { available ->
            if (available) {
                EngineRepository.setMicBlocked(false)
                serviceScope.launch { resumeWakeWordIfAllowed() }
            } else {
                EngineRepository.setMicBlocked(true)
                stopWakeWordListening()
                stopSpeechRecognition()
            }
        }

        createNotificationChannelIfNeeded()
        startForeground(ENGINE_NOTIFICATION_ID, buildNotification())

        settingsCollectionJob = serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                if (!settings.backgroundListeningEnabled) {
                    stopWakeWordListening()
                    stopSpeechRecognition()
                } else {
                    resumeWakeWordIfAllowed()
                }
            }
        }

        microphoneBouncer.start()

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java).setAction(ACTION_START_OVERLAY))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ENGINE -> stopSelf()
            else -> serviceScope.launch { resumeWakeWordIfAllowed() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isShuttingDown = true
        settingsCollectionJob?.cancel()
        silenceWatcherJob?.cancel()
        postSpeechDelayJob?.cancel()
        microphoneBouncer.stop()
        stopWakeWordListening()
        stopSpeechRecognition()
        speechRecognizer?.destroy()
        speechRecognizer = null
        wakeWordEngine.release()
        releaseMediaPlayer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun resumeWakeWordIfAllowed() {
        val settings = settingsRepository.settings.first()
        if (!settings.backgroundListeningEnabled || EngineRepository.state.value.isMicBlocked) return
        if (EngineRepository.state.value.isProcessing || isSttActive) return
        EngineRepository.setOverlayTriggered(false)
        startWakeWordListening()
    }

    private fun startWakeWordListening() {
        try {
            wakeWordEngine.startListening {
                serviceScope.launch { onWakeWordDetected() }
            }
            EngineRepository.setListening(true)
        } catch (_: Exception) {
            EngineRepository.setListening(false)
        }
    }

    private suspend fun onWakeWordDetected() {
        EngineRepository.setOverlayTriggered(true)
        stopWakeWordListening()
        startSpeechRecognition()
    }

    private fun stopWakeWordListening() {
        wakeWordEngine.stopListening()
        EngineRepository.setListening(false)
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this) || isSttActive) return

        partialTranscript = ""
        finalTranscript = ""
        lastSpeechSignalAtMs = System.currentTimeMillis()
        isSttActive = true
        EngineRepository.setLiveTranscription("")

        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            speechRecognizer = it
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                lastSpeechSignalAtMs = System.currentTimeMillis()
            }

            override fun onBeginningOfSpeech() {
                lastSpeechSignalAtMs = System.currentTimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB > -2f) lastSpeechSignalAtMs = System.currentTimeMillis()
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                schedulePostSpeechProcessing()
            }

            override fun onError(error: Int) {
                stopSpeechRecognition()
                serviceScope.launch { resumeWakeWordIfAllowed() }
            }

            override fun onResults(results: Bundle?) {
                finalTranscript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                schedulePostSpeechProcessing()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialTranscript = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partialTranscript.isNotBlank()) {
                    EngineRepository.setLiveTranscription(partialTranscript)
                    lastSpeechSignalAtMs = System.currentTimeMillis()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        )

        startSilenceWatcher()
    }

    private fun startSilenceWatcher() {
        silenceWatcherJob?.cancel()
        silenceWatcherJob = serviceScope.launch {
            while (isSttActive && !isShuttingDown) {
                delay(200)
                val silenceDuration = System.currentTimeMillis() - lastSpeechSignalAtMs
                if (silenceDuration >= 5000) {
                    stopSpeechRecognition()
                    resumeWakeWordIfAllowed()
                    return@launch
                }
            }
        }
    }

    private fun schedulePostSpeechProcessing() {
        postSpeechDelayJob?.cancel()
        postSpeechDelayJob = serviceScope.launch {
            delay(1500)
            val transcript = finalTranscript.ifBlank { partialTranscript }.trim()
            if (transcript.isBlank()) {
                stopSpeechRecognition()
                resumeWakeWordIfAllowed()
                return@launch
            }
            processTranscript(transcript)
        }
    }

    private suspend fun processTranscript(transcript: String) {
        stopSpeechRecognition()
        EngineRepository.setProcessing(true)
        EngineRepository.setAssistantStream("")

        try {
            val settings = settingsRepository.settings.first()
            val response = assistantClient.streamAssistantResponse(
                assistantUrl = settings.assistantUrl,
                prompt = transcript,
                onChunk = { chunk -> EngineRepository.setAssistantStream(chunk) }
            )
            EngineRepository.setAssistantStream(response.text)
            playAudio(response.audioUrl ?: settings.ttsUrl)
        } finally {
            EngineRepository.setProcessing(false)
            EngineRepository.setLiveTranscription("")
            EngineRepository.setOverlayTriggered(false)
            resumeWakeWordIfAllowed()
        }
    }

    private fun stopSpeechRecognition() {
        if (!isSttActive) return
        isSttActive = false
        silenceWatcherJob?.cancel()
        postSpeechDelayJob?.cancel()
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }

    private fun playAudio(url: String?) {
        if (url.isNullOrBlank()) return
        releaseMediaPlayer()
        val player = MediaPlayer()
        mediaPlayer = player

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        try {
            player.setDataSource(url)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { releaseMediaPlayer() }
            player.prepareAsync()
        } catch (_: Exception) {
            releaseMediaPlayer()
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ENGINE_NOTIFICATION_CHANNEL_ID,
            getString(R.string.engine_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, ENGINE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.engine_notification_title))
            .setContentText(getString(R.string.engine_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
