# Changelog

## [0.5.0] - 2025-08-16

### Added
- Proactive background agent: `TimerScheduler`/`InMemoryTimerScheduler`, `CalendarWatcher`, `GmailWatcher`, `ProactiveAgentService`
- Timer tools: `set_timer`, `list_timers`, `cancel_timer`
- Autonomous planning: `Planner` (LLM → JSON steps), `PlanExecutor`, `AutonomousPlanner` (plan → execute → observe → correct)
- Planning tools: `plan_task`, `list_plans`, `execute_plan`, `autonomous_task`

### Changed
- `AgentRuntime.process` is now synchronized (voice and proactive triggers serialize)
- New config: `jozef.proactive.*` and `jozef.planning.*`

## [0.4.0] - 2025-08-16

### Added
- Offline speech-to-text via Vosk (`VoskSpeechRecognizer` + `MicrophoneAudioSource`)
- Wake-word detection (`WakeWordDetector` + `VoskWakeWordDetector`) — "hej Jozef"
- Vosk native-library extraction for fat jars (`VoskNativeLibrary` + `jna.library.path`)
- Automatic Vosk model download + install at startup (`VoskModelManager`)
- Voice activity detection (`EnergyVoiceActivityDetector`) — end-of-utterance detection so commands aren't cut off
- Graceful fallback to console input when the Vosk model is missing

### Changed
- `SpeechCapability` now selects STT (`vosk` | `console`) and an optional wake-word detector
- `VoiceLoop` waits for the wake word before listening for a command (when enabled)
- `VoskSpeechRecognizer` waits for speech + silence (VAD) before returning the command
- `SpeechProperties.SttProperties` gained `auto-download` (default true) — the model is fetched and extracted automatically when missing
- New dependency: `com.alphacephei:vosk:0.3.45`

## [0.3.0] - 2025-08-16

### Added
- Overlay HUD (`SwingOverlay` + `OverlayCapability`) with idle/listening/thinking/speaking/confirm states driven by events
- System tray icon + menu (`TrayCapability`)
- Windows autostart via HKCU Run key (`WindowsAutostartManager`)
- Global hotkey via JNativeHook (`NativeGlobalHotkeyService`) with fat-jar native library extraction
- Long-term memory (`FileLongTermMemory`, `data/memory.json`) + `remember_note` / `recall_notes` / `forget_note` tools
- Context aggregation (`DefaultContextAggregator`) — notes + recent actions injected into the system prompt
- Conversation history now sent to the LLM (multi-turn context)
- State events (listening/speaking) + confirmation events published by the voice loop and agent

### Changed
- `AgentRuntime` accepts a `ContextAggregator` and `MemoryProperties`
- `MemoryProperties` gained a `file` field; new `TrayProperties` (`jozef.tray.*`)
- New dependency: JNativeHook (`com.github.kwhat:jnativehook:2.2.2`)

## [0.2.0] - 2025-08-16

### Added
- Google OAuth 2.0 for desktop apps (RFC 8252 loopback flow) — `GoogleAuthService`
- OAuth token persistence (`GoogleTokenStore`) with automatic refresh
- Google Calendar integration — `GoogleCalendarService` + `calendar_list_events`, `calendar_create_event`, `calendar_delete_event`
- Gmail integration — `GmailService` + `gmail_send` (with confirmation), `gmail_list_recent`, `gmail_read_message`
- Interactive console confirmation service (y/n) for CONFIRM/DANGEROUS actions
- Browser: `executable-path` option (e.g. Brave) alongside Playwright channels
- Unit tests: GoogleTokenStore, GoogleCalendarService, GmailService

### Changed
- `ConfirmationService` is now configurable via `jozef.security.confirmation-provider` (console | auto-deny)
- `jozef.browser.channel` default changed from `msedge` to `chrome`

## [0.1.0] - 2025-08-15

### Added
- Async event bus with graceful shutdown (replaces synchronous SimpleEventBus)
- Command bus (intentional operations) + Action/Tool system + ToolRegistry
- `AgentRuntime` with multi-step LLM tool-calling loop
- LLM provider abstraction: Ollama (local) and OpenAI adapters, config-driven
- `SystemPromptProvider` with configurable persona (pl / concise)
- `DesktopAutomation` interface + real Windows implementation (AWT Robot)
- Windows text-to-speech (System.Speech) + console fallbacks
- Permission + confirmation layer (SAFE / CONFIRM / DANGEROUS)
- Built-in `get_current_time` tool + PC automation tools (open/close/type/keys/search/screenshot)
- Unit tests: EventBus, CommandBus, ToolRegistry, Session, AgentRuntime

### Removed
- Legacy "Life ETA Engine" domain (Goal/Plan/Task/Schedule) and synchronous event/command bus
- Spring Web / REST / MVC / Tomcat dependencies
