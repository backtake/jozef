# Life ETA Engine

## Wizja

Life ETA Engine to osobisty asystent planowania życia oparty na AI. Łączy kalendarz, zadania, nawyki, rutyny i dane z integracji (Google, Spotify, Garmin, Home Assistant) by dostarczyć proaktywne sugestie i szacunki czasu (ETA).

## Stack

- Java 21 LTS
- Spring Boot 3.4
- Gradle (Kotlin DSL)
- SQLite + Flyway
- Lombok
- JUnit 5

## Uruchomienie

```bash
./gradlew bootRun
```

## Architektura

Projekt podzielony jest modułowo — **package by feature**. Każdy moduł biznesowy (`planner`, `eta`, `memory`, itd.) ma własną strukturę warstwową (`api`, `application`, `domain`, `infrastructure`, `config`). Współdzielony kod znajduje się w `common`.

Dokumentacja architektoniczna: `docs/architecture/`
