# Eventy domenowe

Moduły komunikują się przez eventy publikowane w `common/event`.

## Zasada

- Moduł A publikuje event → Moduł B nasłuchuje
- Bez bezpośrednich zależności między modułami
- Używamy `ApplicationEventPublisher` ze Springa

## Przykład

```java
// planner/application
eventPublisher.publishEvent(new PlanCreated(planId, userId));

// eta/application
@EventListener
public void onPlanCreated(PlanCreated event) {
    // oblicz ETA dla nowego planu
}
```
