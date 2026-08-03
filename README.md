# józef

Aplikacja Spring Boot 3.x

## Stack

- Java 21 LTS
- Spring Boot 3.4
- Gradle
- SQLite + Flyway
- SLF4J / Logback
- Jackson
- JUnit 5
- Lombok

## Budowanie i uruchamianie

```bash
# Budowanie
./gradlew build

# Uruchomienie
./gradlew bootRun

# Testy
./gradlew test
```

## Struktura bazy danych

Migracje Flyway znajdują się w `src/main/resources/db/migration/`.

Baza SQLite: `data/jozef.db`
