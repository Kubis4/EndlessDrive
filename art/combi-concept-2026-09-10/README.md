# Combi (station wagon) – návrh modulárneho 2.5D auta

Návrh druhej karosérie do hry postavený **presne v ráme dnešného sedanu**: rovnaký
základ 1472 × 459 px (= 5,60 m), rovnaké osi kolies, rovnaké kotvy dielov a
rovnaké sloty (`ComponentSlot`). Cieľ: aby sa combi dalo v hre skladať z tých istých
dielov ako sedan a aby sa dalo bez zmeny fyziky vyhodnotiť, či sa do hry hodí.

Nič z tohto adresára hra zatiaľ nenačítava – je to podklad na rozhodnutie.

## Súbory

| Súbor | Čo je to |
|-------|----------|
| `combi_blueprint.svg` | Zdroj návrhu. Každá skupina `<g id="part-…">` je jeden PNG sprite v súradniciach základu (ľavý horný roh skupiny = kotva pre `BodyPartCatalog`). Vrstva `guides` ukazuje rám, osi kolies, rázvor a kotvy. |
| `combi_part_spec.json` | Kotvy, veľkosti, sloty, poradie kreslenia a stav (reuse / nové) pre každý diel – strojovo čitateľná verzia tabuľky nižšie. |
| `combi_assembled.png` | Zložené combi so skutočnými `wheel_std.png` v mierke 1 : 1. |
| `combi_exploded.png` | Rozložené diely – čo je samostatný sprite. |
| `preview.html` / `preview.png` | Porovnanie: reálne PNG sedanu zložené podľa `BodyPartCatalog` → combi → kotvy → rozložený pohľad. |
| `build_preview.ps1` | Vygeneruje `preview.html` a PNG (headless Edge/Chrome). |

## Čo sa dodržalo zo sedanu

Namerané priamo z `car_base_body.png` a z inštalovaných sprite-ov (alfa hranice):

- Rám **1472 × 459 px**, `worldWidthM = 5.6` → 262,86 px/m. Škrupina musí ostať
  nepriehľadná až po okraje rámu (`SedanLayers.cropToOpaque` z nej odvodzuje rám).
- Kolesá: zadné `(0.191, 0.913)` → (281, 419), predné `(0.828, 0.913)` → (1219, 419),
  vizuálny polomer `0.083 × 1472 = 122 px`. Oblúky blatníkov r ≈ 127 px okolo (283, 419)
  a (1219, 419). Rázvor 938 px = 3,528 m = `2 × WHEEL_OFFSET_X`. `WHEEL_RADIUS`,
  `BODY_VISUAL_LIFT`, `BODY_CENTER_FY` sa nemenia.
- **Celá predná polovica (x ≥ 440)** – vrchol strechy, čelné sklo, A-stĺpik, línia
  blatníka, nos, oba oblúky, prah, otvory dverí a B-stĺpik – je obkreslená 1 : 1.
- Pás okien (spodná hrana skla) y = 196, strešná lišta y 16–58, spodná hrana dverí 436/438.

## Diely

| Diel (`BodyPart`) | Slot | Sprite | Kotva (px) | Veľkosť | Stav |
|---|---|---|---|---|---|
| škrupina | CHASSIS | `car_base_body_combi` | (0, 0) | 1472 × 459 | **nové** |
| TAILGATE (nahrádza TRUNK) | TRUNK_LID | `car_tailgate_combi` | (8, 14) | 168 × 290 | **nové** |
| TAILLIGHT | TAILLIGHT | `car_taillight_combi` | (92, 200) | 96 × 92 | **nové** (rovnaká veľkosť, nová kotva; sedan má (5, 201)) |
| DOOR_REAR | DOOR_REAR | `car_door_rear_combi` | (340, 45) | 387 × 391 | variant – rovnaký rám ako sedan; ako núdzové riešenie sedí aj sedanové `car_door_rear` |
| CARGO | (nový slot) | `car_cargo_boxes` | (100, 110) | 240 × 90 | voliteľné, za škrupinou |
| DOOR_FRONT | DOOR_FRONT | `car_door_front` | (715, 45) | 381 × 393 | reuse |
| HOOD | HOOD | `car_hood` | (1120, 153) | 330 × 108 | reuse |
| HEADLIGHT | HEADLIGHT | `car_headlight_std` | (1295, 258) | 173 × 125 | reuse |
| BUMPER_FRONT | FRONT_BUMPER | `car_bumper_front_fog` | (1290, 315) | 184 × 151 | reuse |
| BUMPER_REAR | REAR_BUMPER | `car_bumper_rear_std` | (−10, 300) | 211 × 150 | reuse |
| FRONT_SEAT / REAR_SEAT | SEAT_* | `car_seat_front` / `car_seat_rear` | (750, 85) / (430, 120) | – | reuse |
| ROOF_RACK | ROOF_RACK | `car_roof_rack` | (442, −15) | 479 × 48 | reuse (voliteľne dlhý variant (190, −15) 730 × 48) |
| kolesá | TIRE_* | `wheel_*` | podľa `SedanLayers` | 256 × 256 | reuse |

Povinné nové sprite-y sú **3** (škrupina, tailgate, zadné svetlo); odporúčaný je
štvrtý (zadné dvere s rovnou hornou hranou). Všetko ostatné sa berie zo sedanu.

### Zadná časť (to, čo je iné)

- Strecha ostáva rovná až po x = 172 (výška zadnej strechy 1,69 m), potom veko
  sklonené 24° z pántu (172, 16) do (14, 235) a dole po horný okraj nárazníka (y 300).
- **Tailgate je jeden diel v slote `TRUNK_LID`** – hráč ho nájde/namontuje ako dnes
  veko kufra (`ItemCatalog.TRUNK_LID`, loot tabuľky aj uložené hry ostávajú platné).
  Bez neho vidno otvorený zadok (otvor v škrupine od strechy po nárazník), čo je
  čitateľnejšie než dnešné chýbajúce veko sedanu (tenký pásik na palube).
- Zadné bočné sklo 118–336 × 56–198 je pevné a je súčasťou škrupiny (ako čelné sklo).
- Zadné svetlo sedí na zadnom blatníku vedľa veka, aby neostalo „visieť“ vo vzduchu,
  keď veko chýba. `SedanLayers` si stred svetla ráta z kotvy + veľkosti sprite-u,
  brzdová žiara sa teda posunie sama.
- Farby držia pravidlo lakovej masky (`splitPaintLayers`): teplá oranžová = lak,
  modrá = sklo, tmavosivá = plasty. Nič z toho netreba v kóde meniť.

## Hodí sa combi do hry?

Áno, s malými výhradami:

**Za**
- Nulový dopad na fyziku a kameru: rovnaký rázvor, polomer kolies, dĺžka, výška strechy,
  pivot karosérie. Hra ho „vidí“ ako ten istý objekt s inou kresbou.
- 9 z 12 dielov je reuse, čiže loot, obchod, opravy a lakovanie fungujú bez nových
  `ItemDef`. Menia sa len kresby v jednom slote (TRUNK_LID) a poloha zadného svetla.
- Väčšie zadné bočné sklo + veko vytvárajú viditeľný nákladný priestor – prirodzené
  miesto pre expedičný obsah (batožina, kanistre) alebo neskôr pre mechaniku
  nosnosti. Rovná strecha nesie dlhší nosič bez zmeny slotu.
- Silueta je na prvý pohľad iná od sedanu už pri hernom zmenšení (~0,4×), pritom
  má rovnaký štýl (rovnaká predná polovica).

**Výhrady / riziká**
- `ExpeditionEquipment.kt` kreslí lišty a lampu na pevných súradniciach základu
  1472 × 459 (zadná lišta začína na x = 432 vnútri zadných dverí). Tie sedia aj na
  combi, lebo dvere sú v rovnakom ráme; treba len skontrolovať, či strešná lampa
  (kotva 905 / −29) nekoliduje s rovnou zadnou strechou – nekoliduje, je nad prednou
  časťou strechy.
- Ak sa použijú sedanové zadné dvere ako núdzové riešenie, nad ich zaoblenou zadnou
  hornou hranou ostane viditeľný klin rovnej strechy (vyzerá ako hrubý C-stĺpik).
  Preto je combi variant dverí odporúčaný.
- Runtime dnes pozná jednu karosériu: `GameAssets` natvrdo dekóduje
  `R.drawable.car_base_body` a `BodyPartCatalog.specs` je jedna mapa. Na prepínanie
  sedan/combi treba malý refaktor (nižšie). Bez neho by combi sedan **nahradilo**.
- Zadný previs 1,07 m a hmotnosť za zadnou osou nie sú v modeli – ak by sa raz
  riešila fyzika naklápania, combi by malo mať iné rozloženie hmoty než sedan.

## Čo treba v kóde (odhad, keď návrh prejde)

1. `BodyPartCatalog`: z jednej `specs` mapy urobiť `Map<CarBody, Map<BodyPart, BodyPartSpec>>`
   (alebo `object CombiBodyCatalog` s rovnakým rozhraním) + pridať `BodyPart.TAILGATE`
   mapovaný na `ComponentSlot.TRUNK_LID` (alebo len iný drawable pre `BodyPart.TRUNK`
   v combi mape – jednoduchšie, poradie kreslenia ostáva).
2. `GameAssets`: dekódovať škrupinu podľa zvolenej karosérie a podať do `SedanLayers`
   správnu mapu dielov. `SedanLayers` samotné netreba meniť (fx kolies sú rovnaké).
3. Výber karosérie: pole v uloženej hre / novej hre (`CarBody.SEDAN` default, aby staré
   uloženia ostali sedanom).
4. Voliteľné: `BodyPart.CARGO` + slot, dlhý nosič ako `variants` položka.

Približne 3 nové PNG + ~60 riadkov Kotlinu; testy `BodyPartCatalog`/render sa dajú
rozšíriť o parametrizáciu karosérie.

## Ako z návrhu spraviť sprite-y

`combi_blueprint.svg` je 1 : 1 s rámom hry. Postup pre každý `part-…`:
exportovať skupinu samostatne na plátno 1472 × 459 (viewBox `0 0 1472 459`),
orezať na hranice z tabuľky (kotva + veľkosť) a uložiť do `drawable-nodpi` pod
menom zo stĺpca *Sprite*. Predná polovica škrupiny sa môže rovno prevziať z
`car_base_body.png` (x ≥ 440) a domaľovať len zadok, aby materiál sedel s ostatnými
dielmi. Kontrola sedenia: `build_preview.ps1` po zámene `<g>` za `<image>` s novým PNG,
alebo rovno v hre cez `render_car_previews` pipeline.
