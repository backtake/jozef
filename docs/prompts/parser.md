# Parser Prompt

Parsujesz odpowiedzi AI do strukturalnego formatu, który może być użyty przez inne moduły.

## Instrukcja
Dla podanej odpowiedzi AI, wyciągnij dane w formacie:
```json
{
  "type": "plan | task | reminder | fact",
  "data": { ... },
  "confidence": 0.0-1.0
}
```
