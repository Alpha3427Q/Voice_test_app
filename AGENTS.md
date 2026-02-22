# Alice AI - Master Blueprint & Architecture Manifesto

## 1. Project Overview & Build Rules
* **App Name:** Alice AI
* **Minimum SDK:** API 33 (Android 13 / 13 Lite)
* **Target/Compile SDK:** API 34 (STRICT RULE: Do not use SDK 35. Do not use AGP 8.6.0+. Maintain AGP 8.4.2 and Compose BOM 2024.04.01 to guarantee GitHub Actions compatibility).
* **Architecture Support:** `armeabi-v7a` (32-bit) and `arm64-v8a` (64-bit).
* **Theme:** Professional Dark Mode exclusively. Cool, sleek background aesthetics.
* **Core Tech Stack:** Jetpack Compose (UI), Android NDK / `llama.cpp` (Offline Vulkan Engine), Retrofit/OkHttp (Online Ollama & TTS network calls).

## 2. The Dual-Engine AI System
The app features two distinct processing modes that share a unified chat memory:

### A. Online Mode (Ollama Cloud)
* Connects to a user-defined Ollama server URL.
* Queries the server for available models and populates the Model Selector dropdown.
* Handles standard REST API JSON requests matching the Ollama API specification.

### B. Offline Mode (Local Vulkan GGUF)
* Uses a custom JNI (Java Native Interface) bridge to `llama.cpp` compiled with `-DGGML_VULKAN=ON` for GPU acceleration.
* **Strict RAM Rule:** The `.gguf` model selected in Settings must NEVER be loaded into device memory until the user explicitly selects it from the top Model Selection dropdown in the Chat UI. Unselecting it must immediately trigger a memory free/unload event.

### C. Cross-Mode Memory & Context
* **Memory Pool:** The app maintains the last 7 messages (or a lightweight hybrid RAG implementation) as active context. 
* **Seamless Switching:** If a user switches from an Online model to an Offline model mid-conversation, the 7-message context is perfectly preserved and passed to the new engine.
* **Dynamic System Prompt:** Every generation must silently prepend this exact prompt:
  `"Your name is Alice an AI assistant created by Adwaith also known as Aromal and Alpha. Current time is [DYNAMIC_TIME] and date is [DYNAMIC_DATE] and day is [DYNAMIC_DAY]."`

## 3. UI/UX Architecture (Jetpack Compose)

### A. Main Chat Screen
* **Top Bar:** * Features a "+" button for uploading files/images (Multipart processing ready).
  * A Model Selection dropdown pill (shows available Ollama models + the loaded Offline model).
* **Chat Feed (`LazyColumn`):**
  * Distinct, professional bubble styling for User vs. Alice.
  * **Markdown Support:** Full rendering for `**bold**`, italics, and structured code blocks. 
  * **Code Blocks:** Must feature a dedicated "Copy" button inside the code UI frame.
  * **Long-Click Actions:** Long-pressing Alice's reply opens a context menu with "Copy" and "Read Aloud" (triggers TTS).
* **Bottom Bar:** Sleek text input field with a highly visible "Send" icon button.

### B. Settings Screen (Accessed via Bottom Slide/Navigation)
* **Storage Permission Gate:** Requests necessary Android 13+ storage permissions to read local `.gguf` files.
* **Offline Engine Config:** * "Add Model" button (opens Android file picker to select a GGUF file).
  * "Remove Model" button (deletes the path from SharedPreferences and forces a RAM unload).
* **Online Engine Config:**
  * OutlinedTextField for "Ollama Server URL".
* **Audio Config:**
  * OutlinedTextField for "TTS API URL".
* **Save Action:** A prominent Save button to lock in configurations to `DataStore` or `SharedPreferences`.

## 4. Text-To-Speech (TTS) Engine
* Triggered via the "Read Aloud" long-click action.
* **API Call Structure:** Performs an HTTP GET request to the configured TTS URL (Default: `https://lonekirito-asuna3456.hf.space/speak`).
* **Query Params:** URL-encodes the AI's response text (`?text=...`).
* **Audio Handling:** Downloads/streams the resulting `.wav` binary and plays it using Android's native `MediaPlayer`.

## 5. Assets & Branding
* **App Icon:** The Android launcher icon must be explicitly configured in the Manifest and resources to use the local asset named `appicon.png`.
