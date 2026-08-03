# Architektura modułów

Projekt stosuje **Package by Feature** — każdy moduł biznesowy zawiera własne warstwy.

## Struktura modułu

```
[nazwa-modułu]/
├── api/            # Kontrolery REST, DTO, endpointy
├── application/    # Serwisy aplikacyjne, UseCase, porty
├── domain/         # Encje, Value Objects, wyjątki domenowe
├── infrastructure/ # Repozytoria, integracje z DB, klienty HTTP
└── config/         # Konfiguracja Spring dla modułu
```

## Moduły

| Moduł          | Odpowiedzialność                            |
|----------------|---------------------------------------------|
| `planner`      | Plany, cele, kroki                          |
| `eta`          | Szacowanie czasu (predykcje)                |
| `tasks`        | Zadania jednorazowe i cykliczne             |
| `routines`     | Rutyny i nawyki                             |
| `timeline`     | Oś czasu, historia                          |
| `user`         | Użytkownik, preferencje                     |
| `memory`       | Kontekst AI, historia rozmów                |
| `ai`           | Klienty LLM, prompty, parsowanie            |
| `audio`        | STT, TTS, nagrywanie                        |
| `desktop`      | Tray, overlay, hotkeys                      |
| `integrations` | Google, Spotify, Garmin, itd.               |
| `notification` | Powiadomienia push/in-app                   |
| `telemetry`    | Metryki, logowanie zdarzeń                  |
| `common`       | Współdzielone utility, wyjątki, model       |

## Zasady

1. Moduł nie importuje bezpośrednio `infrastructure` innego modułu — używa interfejsów/portów
2. `common` nie ma zależności do modułów biznesowych
3. `api` wystawia tylko DTO — nigdy encje domenowe
4. Eventy (w `common/event`) służą do komunikacji między modułami
