# Jozef — Plan realizacji i status

> Ten plik jest **jedynym źródłem prawdy** o tym, co zostało zrobione i co zostało do zrobienia.
> Aktualizuj go po każdej fazie.

## Cel

Jozef to osobisty agent AI na Windows, działający w tle — w stylu JARVIS:

- ciągłe nasłuchiwanie mikrofonu,
- detekcja słowa budzącego (wake word),
- STT → LLM (wymienny) → narzędzia (tools) → TTS,
- automatyzacja PC, przeglądarki, Gmaila, Kalendarza Google,
- overlay, tray, praca w tle,
- architektura zdarzeniowa, wtyczki (capabilities),
- Windows jest tylko pierwszym targetem — kod platformowy za interfejsami.

**Jozef nie jest aplikacją webową ani REST API.** Spring Boot służy tylko jako kontener DI.

---

## Architektura (docelowa)

```
                    ┌───────────────────┐
                    │      JOZEF        │
                    │   Agent Runtime   │
                    └─────────┬─────────┘
                              │
        ┌─────────────┬───────┼────────┬──────────────┐
        ↓             ↓       ↓        ↓              ↓
     Voice          LLM     Tools   Memory        Context
        │             │       │
        ↓             ↓       ↓
   STT / TTS    Providers   ├── Windows automation
   (replaceable)  Ollama     ├── Browser (Playwright)
                  OpenAI     ├── Gmail
                  DeepSeek   └── Calendar
```

Główne pakiety:

- `core.event` — asynchroniczny EventBus
- `core.command` — synchroniczny CommandBus
- `core.action` — `Action`, `ActionResult`, `ExecutionStrategy`, `LambdaAction`
- `core.tool` — `ToolRegistry`, `ToolDefinition`, `ToolParameters`
- `core.agent` — `AgentRuntime` (pętla rozumowania + tool calling)
- `core.capabilities` / `core.lifecycle` — zarządzanie capabilities i cyklem życia
- `core.security` — `PermissionManager`, `ConfirmationService`
- `core.session` / `core.context` — pamięć rozmowy, system prompt / persona, agregacja kontekstu
- `core.memory` — `LongTermMemory` / `FileLongTermMemory` + tools `remember_note` / `recall_notes` / `forget_note`
- `overlay` — `Overlay` / `SwingOverlay` / `OverlayCapability` (HUD statusu)
- `tray` — `TrayCapability` + autostart + globalny hotkey (JNativeHook)
- `llm` — `LLMProvider` + adaptery (ollama, openai, deepseek)
- `speech` — `SpeechRecognizer` / `SpeechSynthesizer` / `WakeWordDetector` / `SpeechTextCleaner`
  + TTS Edge (neuralne) + STT Vosk (lokalny, offline) + mikrofon (`MicrophoneAudioSource`)
- `automation` — `DesktopAutomation` + implementacja Windows
- `browser` — `BrowserService` + implementacja Playwright
- `proactive` — `TimerScheduler`, `CalendarWatcher`, `GmailWatcher` (agent działający w tle)
- `planning` — `Planner`, `PlanExecutor`, `AutonomousPlanner` (plan → execute → correct)
- `configuration` — `jozef.*` (`@ConfigurationProperties`)
- `bootstrap` — entry point + pętla głosowa

---

## Status faz (Master Implementation Plan)

### ✅ Zrobione

| Faza | Zakres | Status |
|------|--------|--------|
| 1 | Runtime + lifecycle | ✅ `ApplicationLifecycle`, `CapabilityLoader`, `Capability` |
| 2 | Async EventBus + CommandBus | ✅ `AsyncEventBus` (graceful shutdown, izolacja błędów) + testy |
| 3 | Action + Tool system | ✅ `Action`, `ToolRegistry`, `ToolDefinition`, `ToolParameters` |
| 4 | LLM abstraction + provider | ✅ `LLMProvider`, Ollama (pełny tool calling), OpenAI, DeepSeek |
| 5 | Agent execution loop | ✅ `AgentRuntime` z wieloetapową pętlą tool calling + eventy + sesja |
| 6 | Windows automation | ✅ `DesktopAutomation` + `WindowsAutomationService` (AWT Robot) |
| 7 | STT + VAD | ✅ `VoskSpeechRecognizer` (offline), `EnergyVoiceActivityDetector` (VAD), fallback konsola |
| 8 | TTS | ✅ Edge TTS (darmowe neuralne głosy PL) + Windows System.Speech + konsola |
| 9 | Wake word | ✅ `WakeWordDetector` + `VoskWakeWordDetector` („hej Jozef", sterowane konfiguracją) |
| 10 | Browser / Playwright | ✅ `BrowserService` + Playwright (channel chrome/msedge, `executable-path` np. Brave, profil trwały) |
| 11 | Google OAuth | ✅ `GoogleAuthService` (desktop loopback flow), `GoogleTokenStore`, auto-refresh |
| 12 | Calendar | ✅ `GoogleCalendarService` + `CalendarEvent` + tools `calendar_*` |
| 13 | Gmail | ✅ `GmailService` + `GmailMessage` + tools `gmail_*` (send z potwierdzeniem) |
| 14 | Overlay | ✅ `OverlayCapability` + `SwingOverlay` (idle/listening/thinking/speaking/confirm, always-on-top) |
| 15 | System tray + autostart + hotkey | ✅ `TrayCapability`, `WindowsAutostartManager` (HKCU Run), `NativeGlobalHotkeyService` (JNativeHook) |
| 16 | Memory + Context | ✅ `FileLongTermMemory`, `ContextAggregator`, historia rozmowy wysyłana do LLM |
| 17 | Permissions + confirmations | ✅ `PermissionManager`, `ConfirmationService` (console + auto-deny fail-safe) |
| 18 | Proactive background agent | ✅ `TimerScheduler`, `CalendarWatcher`, `GmailWatcher`, `ProactiveAgentService` + tools `set_timer`/`list_timers`/`cancel_timer` |
| 19 | Advanced planning / autonomous execution | ✅ `Planner`, `PlanExecutor`, `AutonomousPlanner` (plan → execute → observe → correct) + tools `plan_task`/`execute_plan`/`autonomous_task` |

Dodatkowo zrobione:

- `SystemPromptProvider` + `ConfigurableSystemPromptProvider` (persona: pl / concise, styl mówiony).
- `SpeechTextCleaner` — czyści odpowiedzi LLM z markdowna przed TTS.
- Naprawa trybu headless (Spring Boot wymuszał `java.awt.headless=true`, przez co AWT Robot nie działał).
- Wbudowane narzędzie `get_current_time` + narzędzia automatyzacji:
  `open_application`, `close_application`, `type_text`, `press_keys`, `search_web`, `take_screenshot`.
- Narzędzia przeglądarki: `browser_navigate`, `browser_search`, `browser_click`, `browser_type`,
  `browser_read_text`, `browser_get_page_text`, `browser_get_title`, `browser_get_url`, `browser_screenshot`.
- Testy jednostkowe (72 zielone): EventBus, CommandBus, ToolRegistry, Session, AgentRuntime, EdgeTts,
  GoogleTokenStore, GoogleCalendarService, GmailService, FileLongTermMemory, DefaultContextAggregator,
  OverlayCapability, WindowsAutostartManager, NativeGlobalHotkeyService, Vosk (STT/wake-word), VAD,
  TimerScheduler, Watchers, Planner, PlanExecutor.
- Vosk (lokalny offline STT) + wake word + VAD; natywne biblioteki Vosk/JNativeHook ekstrahowane z fat-jara do `data/native/`.
- Proaktywność (timery + watchery kalendarza/Gmaila) + planowanie (plan → execute → observe → correct).
- `ConsoleConfirmationService` — interaktywne potwierdzenie y/n (CONFIRM/DANGEROUS).
- Google: leniwe OAuth + Calendar + Gmail (zakresy: calendar, gmail.send, gmail.readonly).
- Overlay sterowany eventami; tray + autostart + globalny hotkey; pamięć długoterminowa + kontekst.
- Cleanup: usunięty legacy `common`, stara domena Goal/Plan/Task/Schedule, SQLite, stare docs.
- `README.md`, `VISION.md`, `CHANGELOG.md` zaktualizowane.

### ⚠️ Częściowo zrobione

Brak — wszystkie fazy 1–19 są domknięte.

### ❌ Do zrobienia (kolejne fazy)

Brak — wszystkie fazy (1–19) są zaimplementowane.

### ❌ Dostawcy LLM do uzupełnienia

- `anthropic` — brak adaptera
- `gemini` — brak adaptera
- (obecnie: `ollama` ✅, `openai` ✅, `deepseek` ✅)

---

## Pierwszy pionowy slice (MVP 1) — cel

> „Jozef słyszy »Jozef, otwórz Chrome« → LLM wybiera `open_browser` → komputer wykonuje akcję → Jozef odpowiada głosem."

Wymagane i ich status:

- [x] proces w tle (Spring Boot no-web)
- [x] mikrofon + prawdziwy STT (Vosk, lokalny offline; fallback konsola)
- [x] LLM provider (ollama/openai/deepseek)
- [x] tool calling (`AgentRuntime`)
- [x] Windows automation (realne mouse/keyboard/clipboard)
- [x] TTS (Edge — neuralne głosy PL)
- [x] wake word (faza 9)

> Automatyzacja działa na realnej sesji desktopowej (AWT Robot). W środowisku headless (CI/SSH) capability „automation" kończy się błędem, ale runtime zostaje przy życiu — to zamierzone, zgodnie z wymogiem odporności.

---

## Kolejność dalszych prac (bez przeskakiwania)

1. **MVP 1:** ✅ mikrofon + Vosk STT + wake word. (VAD na sam koniec.)
2. **MVP 2:** ✅ Google OAuth (11) → Calendar (12) → Gmail (13, z potwierdzeniem wysyłki).
3. **MVP 3:** ✅ overlay (14) → system tray + autostart + hotkey (15) → pamięć + kontekst (16).
4. **Faza 18–19:** ✅ proaktywność (timery + watchery) i planowanie (plan → execute → correct).

Po każdej fazie: kompilacja → testy → (gdzie dotyczy) testy integracyjne → weryfikacja architektury → commit → dopiero następna faza.

---

## Uruchomienie i testy

```bash
# w Git Bash na Windows (workaround na mungowanie argumentów Gradle):
export JAVA_HOME="$HOME/.jdks/graalvm-jdk-21.0.7"
cmd //c "gradlew.bat build"     # kompilacja + testy + bootJar
cmd //c "gradlew.bat bootRun"   # start agenta
```

W `cmd.exe` (wiersz polecenia):

```cmd
set "JAVA_HOME=C:\Users\stasi\.jdks\graalvm-jdk-21.0.7"
gradlew.bat build
"%JAVA_HOME%\bin\java.exe" -jar build\libs\jozef-0.0.1.jar
```

Konfiguracja: `src/main/resources/application.yml` (sekcja `jozef.*`). Sekrety tylko przez zmienne środowiskowe.

**Uwaga:** pierwsze uruchomienie Playwright pobiera binarki przeglądarek (~250 MB) do `%LOCALAPPDATA%\ms-playwright`.

---

## Punkt wznowienia (następna sesja)

Zrobione w tej sesji — **fazy 18 i 19** (ostatnie z roadmapy):

- ✅ **Faza 18: Proactive background agent** — `TimerScheduler`/`InMemoryTimerScheduler` (przypomnienia), `CalendarWatcher` + `GmailWatcher` (proaktywne powiadomienia), `ProactiveAgentService` (wstrzykuje zdarzenie do pętli agenta i mówi wynik). Tools: `set_timer`, `list_timers`, `cancel_timer`.
- ✅ **Faza 19: Advanced planning** — `Planner` (LLM → JSON kroków), `PlanExecutor` (wykonanie z tą samą polityką uprawnień/potwierdzeń), `AutonomousPlanner` (plan → execute → observe → correct, `max-attempts`). Tools: `plan_task`, `list_plans`, `execute_plan`, `autonomous_task`.
- ✅ `AgentRuntime.process` zsynchronizowany (głos i proaktywne trigger nie przeplatają się).
- ✅ 72 testy zielone, pełny build OK, smoke-test (proactive + planning init) OK.

**Stan roadmapy:** wszystkie fazy 1–19 zaimplementowane. Zostały tylko adaptery LLM `anthropic`/`gemini` (opcjonalne).

**Najbliższe kroki (do wyboru):**

1. Adaptery LLM `anthropic` / `gemini`.
2. Polerowanie: overlay z przyciskami potwierdzeń, persystencja sesji/planów, testy integracyjne.

Sekrety tylko przez zmienne środowiskowe: `OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.
