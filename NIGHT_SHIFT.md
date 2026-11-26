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

- [x] P0-1 LLM: ToolCall z id, protokół OpenAI (tool_call_id), timeouty, błędy jako wyjątki.
- [x] P0-2 TTS: Edge zablokowany (DRM) → OpenAI TTS → Windows TTS (Paulina) → konsola.
- [x] P0-3 System prompt: data/godzina/strefa/dzień tygodnia, zasady narzędzi.
- [x] P0-4 Potwierdzenia rozmową (pytanie budowane w kodzie, kanał, 20 s okno głosowe).
- [x] P0-5 Timeout narzędzi + OUTCOME UNKNOWN dla ryzykownych.
- [x] P1-1 Skróty użytkownika (data/shortcuts.yml) + open_url + szybka ścieżka bez LLM.
- [x] P1-2 Programy z Menu Start (Get-StartApps, shell:AppsFolder).
- [x] P1-3 Okienko poleceń (Ctrl+Alt+J) + tray (pauza mikrofonu, autostart, edycja skrótów).
- [x] P1-4 Adapter Anthropic (claude-opus-5-5) + tryb auto.
- [x] P2-1 Telefon: bot Telegram (whitelist, ack, stale, tylko prywatne).
- [x] P2-2 Powiadomienia Windows (dymek z traya).
- [x] P2-3 Narzędzie jozef_status ("co u ciebie działa?") + polskie podpowiedzi błędów LLM.
- [x] P2-4 Autostart (start /D, javaw), jozef.cmd, pojedyncza instancja.
- [ ] P2-5 Adapter Gemini (spec BMAD CAP-2) — niski priorytet.
- [ ] P3 Overlay z przyciskami potwierdzeń (spec CAP-3) — potwierdzenia rozmową pokrywają potrzebę.
- [ ] P3 Długie czytanie maili głosem (limit OpenAI TTS 4096 zn., Windows TTS przez stdin).

## Dziennik

- 00:12 start, watchdog (Task Scheduler `JozefNightWatchdog`, co godzinę) + branch.
- 00:20 review rdzenia, diagnoza powyżej.
- 00:35 JOZEF-25 (P0-1..P0-5) → krytyk: FAIL (B1-B3 potwierdzenia, proaktywność jako "user input").
- 00:45 JOZEF-26 poprawki + P1-1/P1-2 (skróty, Start menu, szybka ścieżka) → krytyk: FAIL (N1 przejęcie skrótów, N2-N10).
- 01:00 JOZEF-27 poprawki rundy 2 + okienko poleceń (P1-3), pauza mikrofonu, cache modelu Vosk,
  naprawa gubienia polecenia po "hej Józef", podpowiedzi błędów LLM, smoke test z prawdziwą Ollamą.
- 01:10 JOZEF-28 Telegram, autostart, README → krytyk: FAIL (autostart nie działał, Telegram replay, 2 instancje).
- 01:15–04:00 limit tokenów. Watchdog odpalał sesje headless (też trafiały w limit); o 04:00 headless
  ruszył minutę przed wznowieniem tej sesji — zatrzymany (duplikat), watchdog przełączony na samo logowanie
  (harness sam wznawia sesję po resecie).
- 04:30 JOZEF-29 adapter Anthropic + jozef_status; JOZEF-30 poprawki rundy 4 → krytyk PASS → **push e9a4bd8**.
- 04:35–04:45 JOZEF-31..38: multimedia, pogoda, gmail_reply + łatka wstrzykiwania nagłówków, nieblokujący
  Google, read_web_page/web_lookup (test na żywo), blokada SSRF, odporność pamięci na treści z WWW/maili,
  ciche pierwsze sprawdzenie Gmaila, powitanie, CHANGELOG/agent.md, Windows TTS przez stdin.
- ~04:45–09:00 drugi limit tokenów (sesja wznowiona automatycznie, watchdog już niczego nie odpalał).
- 09:05 JOZEF-39 (G1: wygasłe tokeny Google) → runda 9 krytyka.

## Decyzje

- Edge TTS usunięty: Microsoft zabezpieczył nieoficjalny endpoint zaciemnionym tokenem (DRM) — nie obchodzimy
  zabezpieczeń; zastępstwo: OpenAI TTS (oficjalne API) → Windows TTS (Paulina, offline).
- Proaktywne komunikaty (timery, maile, kalendarz) NIE idą do LLM jako polecenia — są mówione wprost
  (zamyka wektor prompt-injection: temat maila sterujący agentem / zatwierdzający akcję).
- Potwierdzenia: pytanie budowane w kodzie z prawdziwych argumentów; "tak/nie" klasyfikowane deterministycznie.
- `jozef.llm.provider: auto` (OpenAI → DeepSeek → Ollama). Powód: główna skarga = słabe odpowiedzi małego modelu.
  Cena: rozmowy idą do chmury, gdy jest klucz (log WARN przy starcie). Cofnięcie: `provider: ollama`.
- Osobny planner (plan_task/execute_plan/autonomous_task) domyślnie wyłączony — pętla agenta sama planuje
  wieloetapowo; planner dublował ją, kosztował i psuł potwierdzenia.
- Skróty tworzone przez model: tylko URL / nazwa zainstalowanej aplikacji / protokół (spotify:), zawsze z
  potwierdzeniem; ścieżki do plików tylko ręcznie w data/shortcuts.yml.
- press_keys z Enter/Win, type_text z nową linią → wymagają potwierdzenia (łańcuch: otwórz terminal → wpisz → Enter).

## Problemy dla użytkownika

1. **Konto OpenAI nie ma środków** (`credit_balance_exhausted`). Klucz jest ważny, ale każde zapytanie → 429.
   Teraz Jozef automatycznie przechodzi na lokalną Ollamę (llama3.2 — działa, ale słabo). Doładuj konto OpenAI
   (gpt-4.1-mini to grosze miesięcznie przy osobistym użyciu) albo ustaw `DEEPSEEK_API_KEY`.
2. Ollama: uruchomiłem ją przy diagnozie (`ollama list` startuje serwer); pobrała się aktualizacja 0.40.18 —
   instalator czeka, aż go zatwierdzisz.
3. **Google nie jest skonfigurowane** (brak `data/google-credentials.json`). Przy pierwszym starcie nowej wersji
   Jozef raz otworzy kreator w tle; potem „połącz się z Google” / tray. Ustaw aplikację OAuth na
   *In production*, inaczej Google co 7 dni wymusi ponowne logowanie.
4. **Telefon**: utwórz bota u @BotFather, ustaw `TELEGRAM_BOT_TOKEN`, wpisz swój chat id w
   `jozef.telegram.allowed-chat-ids` (instrukcja w README).
5. Do decyzji: `jozef.llm.provider: auto` wysyła rozmowy do chmury, gdy jest klucz (Claude/OpenAI). Jeśli
   wolisz prywatność kosztem jakości — `ollama`. Domyślne miasto pogody: `jozef.weather.city`.
6. Nie testowałem na żywo (wymaga Ciebie / kont): logowania Google, wysyłki maili, bota Telegram, mikrofonu
   z Vosk, rzeczywistego wpisu autostartu w rejestrze (logikę zapisu sprawdziłem na kluczu testowym).

## Znane drobne ograniczenia (świadomie zostawione)

- Telefon i PC dzielą jedną historię rozmowy; poziom „effort” Claude na sztywno `low`.
- Przy autostarcie przez `cmd /c start` przez ułamek sekundy mignie okno konsoli.
- Czytanie stron w tle: żądania do sieci lokalnej są blokowane filtrem Playwright; przekierowania
  wewnątrz przeglądarki i DNS rebinding to ryzyko szczątkowe.
- Playwright po przekroczeniu czasu narzędzia używany z nowego wątku.
- Wiadomości Telegrama: przy awarii w środku paczki pozostałe wiadomości tej paczki przepadają.
