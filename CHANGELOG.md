# Changelog

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
