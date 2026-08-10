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
- Štart: **náhodne vygenerovaný vrak** — stav dielov 25–78 %, batéria, chladič,
  alternátor či štartér môžu úplne chýbať, nádrže bývajú aj prázdne
  a to, čo v nich je, býva riedené. Karoséria chýba vždy.
- Hneď pri aute je kôlňa s presne tým, čo autu chýba, aby sa dalo naštartovať —
  ďalej už len to, čo nájdeš
- Hra sa nikdy nezasekne: ak sa nedá naštartovať a chýbajúci diel nie je v batohu
  ani v budove nablízku, pri ceste sa objaví vrak, v ktorom ho nájdeš
  (kvapaliny takto maximálne 2× za jazdu)
- Montáž karosérie mení vzhľad (dvere / kapota / okná / nárazníky)
- Motor, kolesá, nádrž, batéria, chladič, olej, palivo, chladiaca kvapalina
- Montáž / demontáž / oprava z inventára

### Kvapaliny
- Palivo, olej a chladiaca kvapalina majú **čistotu**, nie opotrebenie
- Nájdený kanister býva riedený vodou (dom = najhoršie, servis = najlepšie)
- Doliatím sa čistota v nádrži **zmieša** podľa objemu
- Riedené palivo = slabší ťah a vyššia spotreba, špinavý olej ničí motor,
  voda namiesto chladiacej kvapaliny drží motor trvale horúci

### Svet
- Seed → plán segmentu → úseky trate → budovy → loot (všetko RNG:
  0–4 budovy na úsek, obsah budovy od vykradnutej ruiny po jackpot)
- Úseky: **rovinka, kopce, serpentíny, rozbitá cesta, most**
  (menia terén, hrboľatosť, strop rýchlosti aj opotrebenie pneumatík)
- Most má rovnú mostovku, roklinu pod sebou, piliere a zábradlie
- Spotreba rastie do kopca a klesá z kopca; gravitácia ťahá auto po svahu

### Budovy + loot
- Dom, garáž, benzínová stanica, autoservis
- Procedurálny loot podľa typu budovy, vzdialenosti a rarity

### Denný cyklus
- Čas plynie počas celej jazdy (celý cyklus ≈ 10 min), HUD ukazuje herné hodiny
- Obloha, kulisy aj budovy sa menia so svetlom (východ / poludnie / súmrak / noc)
- V noci treba **svetlomety** – bez nich je rýchlosť zastropovaná
- Svetlá berú z batérie; pri vypnutom motore ju vybijú a jazda skončí
- Alternátor dobíja batériu len počas behu motora

### Gameplay loop
1. Štart pri rozbitom aute (tutorial checklist)
2. Doplň kvapaliny, skontroluj komponenty, naštartuj
3. Jazda → zastavenie → prieskum budovy → loot
4. Inventár / montáž / **demontáž** / oprava / tankovanie
5. Na benzínke sa dá tankovať priamo zo stojana (má obmedzenú zásobu)
6. Ďalšia jazda, náhodné udalosti, rekord vzdialenosti

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
| Križovatka | 2–3 vetvy — len názov a nálada, čo je za odbočkou zistíš až jazdou |
| Pri aute | ŠTART, INVENTÁR, AUTO, BUDOVA, JAZDIŤ |
| V budove | loot → ZOBRAŤ, na benzínke NATANKOVAŤ |
| Kedykoľvek | vpravo hore: hodiny, SVETLÁ, PAUZA |
| Panel AUTO | všetkých 14 dielov naraz v mriežke → OPRAVIŤ / DEMONTOVAŤ |

HUD: ukazovatele paliva, oleja, chladenia, batérie, teploty a stavu auta + rýchlosť,
vzdialenosť a rekord.

Bočný 2.5D vizuál (HillRush depth projekcia): cesta má hĺbku, auto je extrudované zboku,
križovatky ukazujú rozvetvenie trate.

### Vizuál
- Procedurálna obloha s denným cyklom (gradient, slnko/mesiac, hviezdy, oblaky)
- Dve parallax vrstvy vzdialených kopcov + pás siluety z bitmapy
- Kulisy pri ceste: stromy, kríky, kamene, ploty, kontajnery, vraky
- Stĺpy elektrického vedenia s previsnutými drôtmi a míľniky každých 100 m
- Budovy podľa typu (dom, garáž, benzínka s prístreškom a stojanmi, autoservis),
  v noci svietia okná, vyrabované ostávajú tmavé
- Efekty auta: tieň, prach spod kolies, dym z výfuku, kužeľ svetlometov v noci

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

- `game/` – `GameEngine`, `Car`, `DayCycle`, `WorldGenerator`, `SegmentPlan`,
  `RoadSegment` (úseky trate), `TerrainProfile`, `LootGenerator`
- `domain/model/` – itemy, rarity, sloty, budovy
- `ui/game/` – `GameRenderer`, `SkyPainter`, `SceneryPainter`, `BuildingPainter`,
  `CarArtist`, HUD, inventár, auto panel

---

## Ďalšie kroky (po MVP)

- Ďalšie biomy (púšť, les, priemysel, mesto)
- Viac budov a legendary loot
- Strešný nosič / extra nádrže
- Audio (motor, rádio, ambient)
- Ukladanie priebehu jazdy (nie len rekordu)
