# Schemat bazy danych

## Silnik

SQLite przez JDBC, zarządzany przez Flyway.

## Pliki migracji

- `database/migrations/` — pliki `V{version}__{opis}.sql`

## Encje

Wszystkie encje używają `@Entity` z Hibernate + SQLite Dialect.

Każdy moduł definiuje własne encje w `domain/`.
