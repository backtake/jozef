# ADR-003: Event-driven komunikacja między modułami

## Status
Accepted

## Date
2025-08-03

## Context
Moduły biznesowe muszą reagować na zdarzenia w innych modułach (np. `eta` reaguje na `PlanCreated` w `planner`). Nie chcemy tworzyć cyklicznych zależności.

## Decision
Używamy Spring `ApplicationEventPublisher` + `@EventListener`. Każdy moduł publikuje eventy do `common/event`, a inne moduły nasłuchują.

## Consequences
- ✅ Brak cyklicznych zależności
- ✅ Łatwe testowanie (eventy in-memory)
- ✅ Naturalna ścieżka do message brokera w przyszłości
- ⚠️ Trzeba pilnować spójności nazw eventów
