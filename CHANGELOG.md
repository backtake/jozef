# Changelog

## [0.11.0] - 2026-11-26 (nocny overhaul, gałąź jozef-overhaul)

### Fixed (rzeczy, które nie działały)
- Tool calling w OpenAI/DeepSeek (brak `tool_call_id` → HTTP 400 przy drugim kroku)
- Błędy dostawcy LLM czytane użytkownikowi jako odpowiedź („Error: …”); brak timeoutów
- Głos: Edge TTS zablokowany przez Microsoft → OpenAI TTS / Windows TTS (Paulina)
- Brak daty i godziny w prompcie („co mam jutro?” nie mogło działać)
- Potwierdzenia przez konsolę (niewykonalne w trayu) → potwierdzenia w rozmowie
- Polecenie po „hej Józef” ginęło, gdy rozpoznawanie zapisało „józef” z „ó”; model Vosk ładowany dwa razy na komendę
- „stop” zamykało aplikację; globalny skrót Ctrl+Alt+J nic nie robił
- Każdy start bez konfiguracji Google otwierał Cloud Console i blokował start nawet na 10 min
- Autostart: okno konsoli, zły katalog roboczy (dane szukane w System32)
- Obserwator Gmaila ogłaszał całą skrzynkę przy każdym starcie

### Added
- Skróty (`data/shortcuts.yml`) i natychmiastowe polecenia bez modelu: „otwórz YouTube”, „uruchom Spotify”, „głośniej”
- Programy z Menu Start, `open_url` w przeglądarce użytkownika, sterowanie multimediami, pogoda (Open-Meteo)
- Okienko poleceń (Ctrl+Alt+J), menu w trayu (pauza mikrofonu, autostart, Google, skróty), powiadomienia Windows
- Telefon: prywatny bot Telegram
- Adapter Claude (Anthropic), tryb `auto` wyboru modelu, polskie komunikaty o przyczynach awarii
- `gmail_reply`, `calendar_events_on_day`, `read_web_page`, `web_lookup`, `jozef_status`, `google_connect`
- Powitanie przy pierwszym starcie, `jozef.cmd`, pojedyncza instancja

### Security
- Pytanie o potwierdzenie budowane w kodzie z prawdziwych argumentów, „tak/nie” deterministycznie, z wiązaniem do kanału
- Proaktywne komunikaty nigdy nie trafiają do modelu jako polecenia
- Śledzenie treści z zewnątrz w turze (WWW, maile): zapis pamięci i otwieranie adresów wymagają potwierdzenia
- Blokada wstrzykiwania nagłówków w Gmailu, adresów lokalnych w `read_web_page`, ścieżek i skryptów w skrótach od modelu
- Klawisze (Enter/Tab/Win…) i tekst z Enterem wymagają potwierdzenia

## [0.10.0] - 2025-10-28

### Changed
- Google first-run setup is now fully automatic — no console typing. Jozef
  opens the Google Cloud Console, then watches Downloads/Desktop/data for the
  downloaded `client_secret*.json` file and continues to the OAuth login
  without any manual input or restart

## [0.9.0] - 2025-10-28

### Added
- Interactive first-run Google setup: when no credentials are configured,
  Jozef opens the Google Cloud Console in the browser, prompts for
  `client_id`/`client_secret`, saves them to `data/google-credentials.json`
  and immediately continues to the OAuth login — no manual file juggling
- Google login is now forced at startup (synchronous, on the main thread): if
  credentials are present but there is no valid token, the browser consent
  flow opens and startup waits for it (with graceful degradation on cancel)
- `GoogleCredentialsSetup` helper (prompt + save + open console)

## [0.8.0] - 2025-10-28

### Added
- Atomic JSON file writes (`AtomicFiles`): memory, plans, session and Google
  tokens are now written via temp-file + atomic move, preventing corruption on
  crash mid-write
- Google credentials from file: `data/google-credentials.json` (the JSON
  downloaded from Google Cloud Console) is now read as a fallback for OAuth
  client id/secret — no secrets in code, no sharing of credentials
- `jozef.google.authenticate-on-startup` (default true): when credentials are
  present but there is no valid token, the browser login opens automatically at
  startup on a background thread

### Changed
- `GoogleProperties` gained `credentials-file` and `authenticate-on-startup`
- `GoogleCapability` prints step-by-step setup instructions when credentials
  are missing

## [0.7.0] - 2025-10-28

### Security
- Fixed shell command injection in desktop automation: application names and
  window titles are now validated/escaped before being embedded in
  `cmd`/PowerShell command lines (new `ShellArgs` helper)
- Hardened `search_web` by quoting the browser URL

### Fixed
- `EventLogger` counter is now an `AtomicInteger` (was a racy `int` on the
  async event bus)
- `WindowsAutomationService.typeText` uses a shared daemon executor instead of
  spawning a new thread per call

### Tests
- Unit tests: `ShellArgsTest`, `SpeechTextCleanerTest`, `DatesTest`,
  `ConfirmationServiceTest`, `PermissionManagerTest`
- Integration (e2e) tests: `AgentEndToEndTest` — full reasoning → tool-calling
  → final-answer loop with real memory/timers/confirmation, starting from the
  user instruction (dictation out of scope)

## [0.6.0] - 2025-10-28

### Added
- LLM fallback: primary provider (Ollama) automatically fails over to a
  secondary provider (DeepSeek) on errors — `jozef.llm.fallback.*`
- Notebook with dates: notes can be attached to a calendar day via the
  optional `date` parameter (`remember_note`), filtered via `recall_notes`
  (by date) and `recall_day_notes`; `MemoryEntry` gained a `date` field
- `daily_briefing` tool (new `AssistantCapability`) — composes notes,
  calendar events and active timers into one "what does my day look like"
- `GoogleCalendarService.listForDay(isoDate)` — events for a specific day
- Persistent plans: `FilePlanStore` (JSON, `data/plans.json`) replaces the
  in-memory store; plans survive restarts
- Persistent sessions: `SessionManager` saves conversation history to
  `data/session.json` and restores it on startup

### Changed
- `LlmProperties` now carries an optional nested `fallback` section
- `PlanningProperties` gained `file`
- `AgentRuntime` persists the session after each turn
- `MemoryCapability` day-keyword resolution (`today`/`jutro`/`wczoraj`) moved
  to a shared `Dates` utility

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
