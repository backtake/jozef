# ADR-001: Package by Feature

## Status
Accepted

## Date
2025-08-03

## Context
Projekt Life ETA Engine będzie rozrastał się o kolejne moduły biznesowe. Tradycyjny podział `controller/service/repository` nie skaluje się przy >10 modułach.

## Decision
Stosujemy **Package by Feature** — każdy moduł biznesowy zawiera własne warstwy: `api`, `application`, `domain`, `infrastructure`, `config`.

## Consequences
- ✅ Łatwa nawigacja — wszystko dla jednej funkcji w jednym miejscu
- ✅ Niskie ryzyko konfliktów merge
- ✅ Naturalne granice dla dekompozycji na mikroserwisy
- ⚠️ Wymaga dyscypliny w izolowaniu modułów (eventy, nie bezpośrednie importy)
