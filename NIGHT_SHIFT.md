# Nocna zmiana — Jozef overhaul (26.11.2026, 00:15 → 12:40)

Autonomiczna praca Claude nad Jozefem. Ten plik jest stanem pracy — każda (także wznowiona
przez watchdog) sesja czyta go na starcie i aktualizuje po każdym kroku.

## Zasady pracy (ustalone z użytkownikiem)

- Gałąź `jozef-overhaul` (od `jozef6`). Push na `origin jozef-overhaul` BEZ czekania na akceptację,
  jeżeli: (1) `gradlew.bat test` zielone, (2) zmiana spełnia założenia produktu (niżej),
  (3) przeszła krytykę osobnego agenta-krytyka (Agent tool, rola: podważać wszystko, szukać dziur).
  Uwagi krytyka poprawić przed pushem (albo uzasadnić w sekcji "Decyzje").
- Nie merguj do master, nie rób PR — użytkownik zrobi to sam.
- Nie wysyłaj maili, nie zmieniaj kalendarza, nie używaj prawdziwego konta Google. Tylko mocki/testy.
- Nie ruszaj `_bmad/`, `_bmad-output/`, `.agents/` (tylko do czytania).
- Blokery → wpisz do "Problemy dla użytkownika" i pracuj dalej nad czymś innym.
- Heartbeat: po każdym kroku `date > ~/.jozef-night/heartbeat.txt` (watchdog odpala nową sesję,
  gdy heartbeat > 50 min). Koniec pracy: 12:40 26.11.2026 (potem użytkownik potrzebuje tokenów).
- Build/test (PowerShell): `$env:JAVA_HOME="$HOME\.jdks\graalvm-jdk-21.0.7"; .\gradlew.bat test`

## Cel produktu (z rozmów z Pi + ustaleń 25/26.11)

Osobisty asystent AI (JARVIS) użytkownika: Gmail + Kalendarz Google, PC (otwieranie konkretnych
stron, uruchamianie programów, wykonywanie poleceń), telefon; docelowo dostęp z telefonu.
Pomoc w pracy, rozrywce i codziennym życiu. "Kilkoma poleceniami" — szybko i niezawodnie.
Kiedyś potencjalnie produkt do sprzedaży (projekt automatyzacji AI) — trzymać konfigurowalność,
brak twardo zakodowanych danych osobistych, czyste granice (kanały wejścia, providerzy).
Użytkownik mówi po polsku. Użytkownik skarżył się na słabe odpowiedzi głosowe (llama3.2 lokalnie).
Przeglądarka: Brave albo Chrome, nie Edge.

## Diagnoza (review 00:15)

Błędy krytyczne (rdzeń nie działa jak obiecano):
1. OpenAI/DeepSeek tool calling zepsuty: id wywołań = "call_"+nazwa, wyniki narzędzi bez
   `tool_call_id` → API zwraca 400 przy drugim kroku. Fallback DeepSeek też nie działa z narzędziami.
2. Błędy providera wracają jako treść "Error: OpenAI returned 400" i są czytane użytkownikowi.
   Brak timeoutów HTTP (zawieszenie na zawsze). `tool-timeout-seconds` nieużywane.
3. Edge TTS: handshake WebSocket odrzucany (Microsoft wymaga nagłówka Sec-MS-GEC) → głos nie działa,
   brak fallbacku na Windows TTS.
4. System prompt bez aktualnej daty/godziny/dnia tygodnia → "co mam jutro" nie ma prawa działać.
5. Potwierdzenia przez konsolę (stdin) — w aplikacji w trayu/autostarcie nie ma konsoli → każda
   akcja CONFIRM (gmail_send itd.) jest de facto niewykonalna. Powinno być rozmową: "Wysłać? — Tak."

Błędne założenia biznesowe:
6. "Otwórz YouTube" → brak narzędzia otwierającego URL w zwykłej (zalogowanej) przeglądarce
   użytkownika; Playwright to osobny profil bez sesji. Brak skrótów użytkownika (moje strony, moje
   programy). Każde proste polecenie idzie przez LLM (wolno, zawodnie na małym modelu).
7. open_application zna ~10 twardo zakodowanych programów; reszta "start <nazwa>" zwykle zawodzi.
   Brak wykrywania zainstalowanych programów (Menu Start .lnk).
8. Jedyne wejście tekstowe to konsola; brak okienka poleceń pod hotkeyem; brak dostępu z telefonu.
9. Domyślny model llama3.2 (3B) słabo radzi sobie z ~40 narzędziami i polskim.

## Backlog (kolejność realizacji)

- [ ] P0-1 LLM: ToolCall z id, poprawny protokół OpenAI (tool_call_id), timeouty, błędy jako wyjątki
      (fallback działa, użytkownik słyszy ludzki komunikat), testy mapowania.
- [ ] P0-2 Edge TTS: Sec-MS-GEC + fallback na Windows TTS; test sieciowy nie może łamać builda.
- [ ] P0-3 System prompt: data/godzina/strefa/dzień tygodnia, zasady użycia narzędzi, PL.
- [ ] P0-4 Potwierdzenia rozmową (oczekujące potwierdzenie w sesji, "tak"/"nie" w kolejnej turze),
      działa z głosem, okienkiem i telefonem.
- [ ] P0-5 Timeout narzędzi (tool-timeout-seconds).
- [ ] P1-1 Skróty użytkownika: data/shortcuts.yml (strony, programy, frazy) + `open_url` (domyślna
      przeglądarka) + szybka ścieżka bez LLM dla "otwórz/uruchom X".
- [ ] P1-2 Wykrywanie zainstalowanych programów (Menu Start .lnk) dla open_application.
- [ ] P1-3 Okienko poleceń (hotkey) — wejście tekstowe niezależne od konsoli; tray "Napisz polecenie".
- [ ] P1-4 Adapter Anthropic (spec BMAD CAP-1) + rozsądny wybór modelu.
- [ ] P2-1 Dostęp z telefonu: bot Telegram (whitelist chat id, token z env) jako kanał wejścia.
- [ ] P2-2 Powiadomienia Windows (toast) dla proaktywnych komunikatów.
- [ ] P2-3 Diagnostyka startu ("doctor"): co działa, czego brakuje, jak naprawić.
- [ ] P2-4 Uruchamianie bez IDE (skrypt startowy/jar, autostart z javaw).
- [ ] P2-5 Adapter Gemini (spec BMAD CAP-2).

## Dziennik

- 00:12 start, watchdog (Task Scheduler `JozefNightWatchdog`, co godzinę) + branch.
- 00:20 review rdzenia, diagnoza powyżej.

## Decyzje

## Problemy dla użytkownika
