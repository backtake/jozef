# Changelog

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
