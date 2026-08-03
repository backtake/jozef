# ADR-002: SQLite jako baza danych

## Status
Accepted

## Date
2025-08-03

## Context
Aplikacja ma działać lokalnie, bez zewnętrznych zależności infrastrukturalnych. Priorytet: prostota wdrożenia i zero konfiguracji.

## Decision
Używamy SQLite przez JDBC + Hibernate z community dialect. Migracje: Flyway.

## Consequences
- ✅ Zero instalacji — plik `.db` w katalogu `data/`
- ✅ Backup to kopia pliku
- ✅ Idealne na single-user desktop app
- ⚠️ Brak współbieżności dla wielu użytkowników (OK dla desktopowej aplikacji)
- ⚠️ Ograniczony zestaw funkcji SQL (rekompensowane przez Hibernate)
