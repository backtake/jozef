# Jozef

Personal desktop AI agent for Windows — inspired by JARVIS.

Jozef is **not** a web app, **not** a REST API, and **not** primarily a chatbot.
It is a background desktop agent: voice in, LLM reasoning, tool execution, voice out.

## Stack

- Java 21 LTS
- Spring Boot 3.4 (DI container only — no web, no REST, no Tomcat)
- Gradle (Kotlin DSL)
- Lombok
- JUnit 5 + AssertJ
- Vosk (offline speech-to-text) + JNativeHook (global hotkey)

## Architecture

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

Key packages:

- `core.event` — async event bus (the nervous system)
- `core.command` — synchronous command bus (intentional operations)
- `core.action` — `Action` (what Jozef can do), `ActionResult`, `ExecutionStrategy`
- `core.tool` — `ToolRegistry` (exposes tool definitions to the LLM)
- `core.agent` — `AgentRuntime` (the reasoning/tool-calling loop)
- `core.capabilities` / `core.lifecycle` — capability & lifecycle management
- `core.security` — `PermissionManager`, `ConfirmationService`
- `core.session` / `core.context` — conversation memory, system prompt/persona, context aggregation
- `core.memory` — `LongTermMemory` / `FileLongTermMemory` + `remember_note` / `recall_notes` / `forget_note`
- `llm` — provider abstraction (`LLMProvider`) + Ollama/OpenAI/DeepSeek adapters
- `speech` — replaceable `SpeechRecognizer` / `SpeechSynthesizer` / `WakeWordDetector`
  (console + Vosk offline STT; Edge TTS)
- `automation` — `DesktopAutomation` abstraction + Windows implementation
- `overlay` — `Overlay` / `SwingOverlay` (status HUD)
- `tray` — `TrayCapability` + autostart + global hotkey (JNativeHook)
- `google` — `GoogleAuthService` (OAuth) + `GoogleCalendarService` / `GmailService`
- `configuration` — Spring `@ConfigurationProperties` (`jozef.*`)
- `bootstrap` — entry point + voice loop

## Run

```bash
./gradlew bootRun
```

On Windows Git Bash, if Gradle argument munging occurs, use:

```bash
cmd //c "gradlew.bat bootRun"
```

Jozef starts as a background process (no web server), initializes capabilities,
and enters the voice loop. STT defaults to Vosk (offline, local model); if the
model is missing it falls back to console input. The wake word is "hej Jozef".
The default TTS is Edge (neural Polish voices).

## Configuration

Everything is configured under `jozef.*` in `src/main/resources/application.yml`.
Secrets (OpenAI/DeepSeek API keys, Google OAuth credentials) are read from
environment variables (`OPENAI_API_KEY`, `DEEPSEEK_API_KEY`, `GOOGLE_CLIENT_ID`,
`GOOGLE_CLIENT_SECRET`), never committed.

## Test

```bash
./gradlew test
```

## Status

First vertical slice (voice → LLM → tools → Windows automation → voice) is wired
end-to-end, including offline Vosk STT and the "hej Jozef" wake word. MVP 2
(Playwright browser, Google OAuth/Calendar/Gmail) and MVP 3 (overlay HUD, system
tray + autostart + global hotkey, long-term memory + context) are also done.
Remaining roadmap: VAD, proactive agent.
