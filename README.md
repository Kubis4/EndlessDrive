# Endless Drive

2.5D mobile survival driving / exploration hra. Jazdíš po **nekonečnej procedurálne
generovanej ceste**, staráš sa o auto, lootuješ opustené budovy a snažíš sa dostať
čo najďalej.

Inšpirované atmosférou *The Long Drive* / *Drive Beyond Horizons*, ale s vlastným
stylized low-poly/retro vizuálom a mechanikami postavenými okolo auta ako hlavnej
postavy.

---

## MVP (táto verzia)

### Auto
- **Sedan** (geometria a 2.5D extrudovanie z HillRush)
- Štart: hrdzavé auto **bez dverí, kapoty, okien a nárazníkov**
- Montáž karosérie mení vzhľad (dvere / kapota / okná / nárazníky)
- Motor, kolesá, nádrž, batéria, chladič, olej, palivo, chladiaca kvapalina
- Montáž / demontáž / oprava z inventára

### Svet
- 1 biome (vidiek)
- Seed → chunk → road segment → buildings → loot
- Chunk streaming (load ahead / unload behind)
- Typy ciest: rovinka, zákruty, rozbitá cesta, most

### Budovy + loot
- Dom, garáž, benzínová stanica, autoservis
- Procedurálny loot podľa typu budovy, vzdialenosti a rarity

### Gameplay loop
1. Štart pri rozbitom aute (tutorial checklist)
2. Doplň kvapaliny, skontroluj komponenty, naštartuj
3. Jazda → zastavenie → prieskum budovy → loot
4. Inventár / montáž / oprava / tankovanie
5. Ďalšia jazda, náhodné udalosti, rekord vzdialenosti

---

## Stack

- Kotlin, Jetpack Compose, DataStore
- Vlastný herný engine (bez Box2D) – arcade jazda optimalizovaná pre mobil
- Procedurálne kreslenie Canvas (žiadne bitmapové assety trate)

Balík: `sk.kubis.endlessdrive`

---

## Ovládanie

| Situácia | Ovládanie |
|---|---|
| Jazda | PRÁVA strana: BRZDA / PLYN (bočný pohľad) |
| Zastavenie | tlačidlo ZASTAVIŤ |
| Križovatka | 2–3 veľké voľby (Vidiek / Priemysel / Skratka) |
| Pri aute | ŠTART, INVENTÁR, AUTO, BUDOVA, JAZDIŤ |
| V budove | loot → ZOBRAŤ |

HUD: palivo, olej, teplota, rýchlosť, vzdialenosť, stav auta.

Bočný 2.5D vizuál (HillRush depth projekcia): cesta má hĺbku, auto je extrudované zboku, križovatky ukazujú rozvetvenie trate.

---

## Build

1. Android Studio → Open → `EndlessDrive`
2. JDK 17, Android 7.0+ (API 24)
3. Run na zariadení / emulátore (landscape)

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

---

## Architektúra (priorita systémov)

```
Driving → Car components → Inventory → Items
  → Road generation → Chunk streaming → Buildings → Loot
  → Repair → UI → Events → (neskôr biomy / audio / polish)
```

Kľúčové balíky:

- `game/` – `GameEngine`, `Car`, `ChunkManager`, `WorldGenerator`
- `domain/model/` – itemy, rarity, sloty, budovy
- `ui/game/` – renderer, HUD, inventár, auto panel

---

## Ďalšie kroky (po MVP)

- Ďalšie biomy (púšť, les, priemysel, mesto)
- Viac budov a legendary loot
- Strešný nosič / extra nádrže
- Audio (motor, rádio, ambient)
- Ukladanie priebehu jazdy (nie len rekordu)
