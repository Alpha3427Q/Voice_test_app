package com.projectalice.engine

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor

class MicrophoneBouncer(
    context: Context,
    private val onMicrophoneAvailableChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var callInProgress = false
    private var externalRecordingActive = false
    private var audioFocusLost = false
    private var microphoneAvailable = true

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> audioFocusLost = false

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioFocusLost = true
        }
        publishAvailability()
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else {
            null
        }

    private val telephonyCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    callInProgress = state != TelephonyManager.CALL_STATE_IDLE
                    publishAvailability()
                }
            }
        } else {
            null
        }

    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            callInProgress = state != TelephonyManager.CALL_STATE_IDLE
            publishAvailability()
        }
    }

    private val mainExecutor = Executor { runnable ->
        Handler(Looper.getMainLooper()).post(runnable)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recordingCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                externalRecordingActive = configs.any { it.clientUid != android.os.Process.myUid() }
                publishAvailability()
            }
        }
    } else {
        null
    }

    fun start() {
        requestAudioFocus()
        registerTelephonyListener()
        registerRecordingCallback()
        publishAvailability()
    }

    fun stop() {
        abandonAudioFocus()
        unregisterTelephonyListener()
        unregisterRecordingCallback()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: return
            val result = audioManager.requestAudioFocus(request)
            audioFocusLost = result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            audioFocusLost = result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun registerTelephonyListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = telephonyCallback ?: return
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (_: SecurityException) {
            callInProgress = false
        }
    }

    private fun unregisterTelephonyListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {
            // No-op.
        }
    }

    private fun registerRecordingCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                recordingCallback?.let { audioManager.registerAudioRecordingCallback(it, mainHandler) }
            } catch (_: SecurityException) {
                externalRecordingActive = false
            }
        }
    }

    private fun unregisterRecordingCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                recordingCallback?.let { audioManager.unregisterAudioRecordingCallback(it) }
            } catch (_: Exception) {
                // No-op.
            }
        }
    }

    private fun publishAvailability() {
        val available = !callInProgress && !externalRecordingActive && !audioFocusLost
        if (available == microphoneAvailable) return
        microphoneAvailable = available
        onMicrophoneAvailableChanged(available)
    }
}
