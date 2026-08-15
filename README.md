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
   (replaceable)  Ollama     ├── Browser (Playwright, planned)
                  OpenAI     ├── Gmail (planned)
                             └── Calendar (planned)
```

Key packages:

- `core.event` — async event bus (the nervous system)
- `core.command` — synchronous command bus (intentional operations)
- `core.action` — `Action` (what Jozef can do), `ActionResult`, `ExecutionStrategy`
- `core.tool` — `ToolRegistry` (exposes tool definitions to the LLM)
- `core.agent` — `AgentRuntime` (the reasoning/tool-calling loop)
- `core.capabilities` / `core.lifecycle` — capability & lifecycle management
- `core.security` — `PermissionManager`, `ConfirmationService`
- `core.session` / `core.context` — conversation memory and system prompt/persona
- `llm` — provider abstraction (`LLMProvider`) + Ollama/OpenAI adapters
- `speech` — replaceable `SpeechRecognizer` / `SpeechSynthesizer`
- `automation` — `DesktopAutomation` abstraction + Windows implementation
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
and enters the voice loop. The default STT is console input (type commands);
the default TTS is Windows System.Speech.

## Configuration

Everything is configured under `jozef.*` in `src/main/resources/application.yml`.
Secrets (e.g. OpenAI API keys) are read from environment variables, never committed.

## Test

```bash
./gradlew test
```

## Status

First vertical slice (voice → LLM → tools → Windows automation → voice) is wired
end-to-end. Remaining roadmap: VAD, wake word, Playwright browser, Google OAuth /
Calendar / Gmail, overlay UI, system tray, long-term memory, proactive agent.
