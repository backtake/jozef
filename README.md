# Jozef

Osobisty asystent AI na Windows, w stylu JARVIS-a. Działa w tle (ikona w trayu),
słucha „hej Józef”, rozumie polski, otwiera strony i programy, obsługuje Gmaila
i Kalendarz Google, pamięta notatki, przypomina. Można do niego pisać z komputera
(Ctrl+Alt+J) i z telefonu (prywatny bot Telegram).

Jozef **nie jest** aplikacją webową ani czatem. Spring Boot służy wyłącznie jako
kontener DI; LLM jest wymiennym „mózgiem”, a głos, okienko i telefon to wymienne
kanały wejścia.

## Szybki start

```powershell
$env:JAVA_HOME="$HOME\.jdks\graalvm-jdk-21.0.7"
.\gradlew.bat bootJar          # buduje build\libs\jozef-0.0.1.jar
.\jozef.cmd                    # uruchamia Jozefa w tle (bez okna konsoli)
```

W trayu: **Napisz polecenie…**, **Wstrzymaj słuchanie** (mikrofon zwolniony),
**Uruchamiaj z Windowsem**, **Edytuj skróty**, **Zakończ**.

Do testów z konsolą: `java -jar build\libs\jozef-0.0.1.jar --jozef.speech.stt.provider=console`.

## Co umie

| Polecenie (przykłady) | Jak działa |
|---|---|
| „otwórz YouTube”, „wejdź na allegro.pl”, „uruchom Spotify”, „odpal Discorda” | od razu, bez modelu (skróty + programy z Menu Start) |
| „co mam jutro w kalendarzu?”, „dodaj spotkanie z Anią w piątek o 14” | Google Calendar (dodanie/usunięcie wymaga potwierdzenia) |
| „czy mam nowe maile od szefa?”, „wyślij Ani maila, że się spóźnię” | Gmail (wysyłka wymaga potwierdzenia) |
| „zapamiętaj, że lubię kawę bez cukru”, „co o mnie wiesz?” | pamięć długoterminowa (`data/memory.json`) |
| „przypomnij mi za 20 minut o praniu” | timer + komunikat głosowy |
| „jak wygląda mój dzień?” | podsumowanie: kalendarz + notatki + timery |
| „dodaj skrót: poczta firmowa to outlook.office.com” | własne skróty (`data/shortcuts.yml`, edycja z traya) |

**Potwierdzenia.** Wszystko, co wysyła, usuwa albo „naciska Enter” za Ciebie,
Jozef najpierw opisuje i pyta: *„Potwierdź: wysłać e-mail do … Mam to zrobić?
Powiedz tak albo nie.”* Pytanie buduje program z prawdziwych danych akcji, nie
model, a odpowiedź „tak” lub „nie” jest rozpoznawana deterministycznie. Komunikaty
proaktywne (przypomnienia, nowe maile) są tylko czytane i nigdy nie stają się
poleceniami.

## Konfiguracja

Wszystko jest w `src/main/resources/application.yml` (sekcja `jozef.*`). Sekrety
podawaj tylko przez zmienne środowiskowe, nigdy w plikach.

| Co | Ustawienie | Uwagi |
|---|---|---|
| Model językowy | `jozef.llm.provider: auto` | Claude (`ANTHROPIC_API_KEY`), potem OpenAI (`OPENAI_API_KEY`), DeepSeek (`DEEPSEEK_API_KEY`), potem lokalna Ollama. `ollama` = wszystko zostaje na komputerze |
| Głos | `jozef.speech.tts.provider: auto` | OpenAI TTS, gdy rozmowa i tak idzie do OpenAI; inaczej Windows (Paulina, offline) |
| Mowa → tekst | `jozef.speech.stt` | Vosk offline; model pobiera się sam przy pierwszym starcie |
| Google | `jozef.google` | przy pierwszym starcie kreator OAuth w przeglądarce |
| Telefon | `jozef.telegram` | patrz niżej |

### Telefon (Telegram)

1. W Telegramie napisz do **@BotFather** `/newbot` i skopiuj token.
2. Ustaw zmienną `TELEGRAM_BOT_TOKEN` (Panel sterowania → Zmienne środowiskowe).
3. Uruchom Jozefa i napisz do swojego bota. Odpisze Twoim *chat id*.
4. Wpisz je w `jozef.telegram.allowed-chat-ids: [twoje_id]` i uruchom Jozefa ponownie.

Bot łączy się tylko „na zewnątrz” (żadnych otwartych portów). Obcych, grupy i
wiadomości przekazane ignoruje. Wiadomości sprzed ponad 3 minut (np. gdy komputer
spał) pomija i o tym informuje. Akcję zleconą z telefonu potwierdzasz z telefonu, a
zleconą przy komputerze potwierdzasz przy komputerze.

**Prywatność:** czaty z botami Telegrama nie są szyfrowane end-to-end. Treść poleceń
i odpowiedzi (np. fragmenty maili) przechodzi przez serwery Telegrama.

## Architektura (skrót)

```
 głos (Vosk) ─┐
 okienko ─────┼─► AgentRuntime ─► szybka ścieżka (skróty) ─┐
 Telegram ────┘        │                                    ▼
                       └─► LLM (OpenAI/DeepSeek/Ollama) ─► ToolRegistry ─► akcje
                                                              (PC, przeglądarka, Gmail,
                                                               Kalendarz, pamięć, timery)
```

Najważniejsze pakiety: `core.agent` (pętla, potwierdzenia, szybka ścieżka),
`llm` (adaptery + fallback), `automation` (launcher, Menu Start, klawiatura),
`shortcuts`, `google`, `speech`, `channels.telegram`, `desktop` (okienko poleceń),
`tray`, `proactive`, `core.memory`.

## Testy

```powershell
.\gradlew.bat test
```

Testy nie wymagają sieci, kluczy, mikrofonu ani Google. Zewnętrzne API są
zastąpione lokalnymi serwerami HTTP albo atrapami.
