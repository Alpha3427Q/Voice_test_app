# PROJECT ALICE: Master Architecture Spec & Blueprint

## 1. Core Architecture (MVVM)
* **Language:** Kotlin (Version 2.1.20)
* **UI Framework:** Jetpack Compose (BOM 2026.01.01)
* **Architecture:** Strict MVVM. The UI (View) only observes StateFlows from ViewModels. Background services communicate with ViewModels via BroadcastReceivers or singleton repositories.
* **App Icon:** The custom launcher icon is located at `res/mipmap-xxhdpi/ic_launcher.png`. Ensure the `AndroidManifest.xml` is configured to use `@mipmap/ic_launcher`.

## 2. CI/CD & GitHub Actions Compatibility
* **Gradle Wrapper:** The project MUST include a functional Gradle Wrapper (`gradlew`). Do not rely on local system Gradle installations.
* **Environment Agnostic:** Never use local absolute paths (like `/root/` or `/sdcard/`) in the `build.gradle.kts` files. Everything must be relative to the project root.
* **Action Setup:** When building workflows, strictly use `gradle/actions/setup-gradle` to handle artifact caching and JDK setup (Temurin 17) to optimize cloud build times.

## 3. Feature 1: The Engine (Background Service)
* Must be a `ForegroundService` with a persistent silent notification to prevent Android from killing it.
* **Wake Word:** Uses Picovoice Porcupine to listen for `Hey-Alice_en_android_v4_0_0.ppn` from the `assets` folder.
* **STT Logic:** Upon trigger, switch to Google's streaming Speech-to-Text (`SpeechRecognizer`). Wait exactly 1.5 seconds after talking stops before processing. If silence lasts for 5 seconds, disable STT and return to `.ppn` wake word listening.
* **Execution:** Stream the finalized STT text to the designated assistant URL, wait for response, and stream audio playback.

## 4. Feature 2: Call & Microphone Detection (The Bouncer)
* Use `AudioManager.OnAudioFocusChangeListener` and `TelephonyManager`.
* If a normal call, WhatsApp, Telegram, or any other app requests the microphone, instantly pause the `.ppn` listener and surrender audio focus. Automatically resume listening only when the call/usage ends.

## 5. Feature 3: The Overlay UI (System Alert Window)
* A system-wide floating window (`SYSTEM_ALERT_WINDOW`).
* **Visuals:** Translucent/blurred background. A centralized, animated, colorful Siri-style orb.
* **Data:** A Google Assistant-style pill bar below the orb displaying real-time STT input and streaming AI text output. Auto-plays AI audio while displaying text.

## 6. Feature 4: Context Injection (App Usage)
* Use `UsageStatsManager` to gather the currently open app's package name and usage details when the AI requests screen context.

## 7. Feature 5: Main App UI (Ollama Chat Command Center)
* **UI:** Jetpack Compose chat interface. Includes a model switcher dropdown in the top bar.
* **Input:** Text field with a `+` button to attach files, images, and audio.
* **Processing:** Set `stream = true` for Ollama. Output must render instantly in chunks.
* **Formatting:** Full Markdown support (bolding, lists, etc.) and real code blocks with a "Copy Code" button. Include message timestamps.
* **Memory:** Hybrid RAG fallback (send the last 7 messages in the context window).
* **Persona:** Inject system prompt: "I AM ALICE AN AI ASSISTANT CREATED BY ADWAITH ALSO KNOWN AS ALPHA AND AROMAL."
* **Gestures:** Long-press on AI replies opens a popup with "Copy" and "Read Aloud". "Read Aloud" sends text to a TTS URL, plays the audio, and shows a top-bar play/pause controller.

## 8. Feature 6: N8N Webhook Chat UI (Asynchronous)
* A separate chat tab functioning like WhatsApp for webhook-based N8N interactions.
* **Logic:** User sends message/media -> pushed to N8N webhook -> waits for N8N to process.
* **Reception:** N8N sends back text/audio. App triggers a standard Android system notification.
* **Media:** Received audio does *not* auto-play. It appears as a playable audio bubble with a Play/Pause button. Chat history is saved locally (Room Database) permanently.

## 9. Feature 7: Settings & Permissions Switchboard
* **Storage:** Use Jetpack DataStore (Preferences).
* **Toggles:** Master switch for Background Listening.
* **Inputs:** Text fields for TTS URL, N8N Webhook URL, Ollama URL, etc.
* **Gatekeeper:** On every app launch, verify all permissions (`RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`, `PACKAGE_USAGE_STATS`, Battery Optimization exemption). Prompt user for missing permissions immediately.
