# Endless Drive

### Expedition polish (September 2026)

- Audio comfort: normal braking is silent; tyre squeal is a short cue (under one
  second) and stays silent during sustained wheelspin. It rearms after grip returns.
  Ice entry no longer squeals. Surface ambience is quieter, impacts are spaced out,
  and consecutive mud/water samples use different variants. SETTINGS offers saved
  master, road/weather and tyre volume, including fully silent tyres.
- UI: graphite panels with amber primary actions, higher secondary-text contrast,
  adaptive dashboard controls and a fading instrument background. The menu separates
  branding from a clear run card; replacing an existing run asks for confirmation.

- Each new kilometre awards **3 repair scrap**. The trip badge shows distance to
  the next reward. Rewards use saved maximum progress, so reversing or loading
  a run does not duplicate them.
- During quiet driving, a compact route objective suggests the next priority:
  first kilometre, first depot, reserves, then winter preparation from 7 km.
  These are guidance milestones, not timed missions.
- Road events cannot repeat consecutively within a session. At most two timed
  adverse events can overlap; immediate hazards still depend on driving conditions.
- Engine audio now has virtual gear changes with hysteresis, a brief shift dip,
  and audible idle while parked. Radio and animal events play their existing
  samples. Pausing/backgrounding suspends one-shots as well as loops.
- Sun and moon bloom use a smooth radial gradient; low sun adds warm horizon light.
- `JourneyTest` covers kilometre boundaries and save/reload reward protection.
  Android smoke tests cover panels, driving and pause behavior.
- Parking handling: front springs are 10% firmer and rear springs 10% softer,
  reducing unloaded rear ride height while keeping real engine/cargo mass distribution.
  Reverse torque is metered to available grip on firm dry roads and uses gentler
  weight transfer. It requires a running engine and fuel. Genuine reverse wheelspin
  rotates driven wheels faster backwards; blur follows each axle and spray changes
  direction, including both driven axles on AWD. `ParkingTractionTest` covers this
  with poor tyres on asphalt, slip on ice, engine-off behavior and cargo sag.

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
  ani v budove nablízku, **za autom** sa objaví jeden vrak, v ktorom ho nájdeš
  (vždy ten istý, nie nový pri každom pokuse; kvapaliny max. 2× za jazdu)
- Montáž karosérie mení vzhľad (dvere / kapota / okná / nárazníky)
- Motor, kolesá, nádrž, batéria, chladič, olej, palivo, chladiaca kvapalina
- Montáž / demontáž / oprava z inventára

### Kvapaliny
- Palivo, olej a chladiaca kvapalina majú **čistotu**, nie opotrebenie
- Nájdený kanister býva riedený vodou (dom = najhoršie, servis = najlepšie)
- Doliatím sa čistota v nádrži **zmieša** podľa objemu
- Riedené palivo = slabší ťah a vyššia spotreba, špinavý olej ničí motor,
  voda namiesto chladiacej kvapaliny drží motor trvale horúci
- V batohu začínaš s **vodou** – núdzovka do chladiča, ktorá objem doplní,
  ale čistotu zrazí takmer na nulu
- V budove vidíš pri každom diele porovnanie s tým, čo máš práve v aute

### Svet
- Seed → plán segmentu → úseky trate → budovy → loot (všetko RNG:
  0–4 budovy na úsek, obsah budovy od vykradnutej ruiny po jackpot)
- Úseky: **rovinka, kopce, serpentíny, rozbitá cesta, most**
  (menia terén, hrboľatosť, strop rýchlosti aj opotrebenie pneumatík)
- Most má rovnú mostovku, roklinu pod sebou, piliere a zábradlie
- Spotreba rastie do kopca a klesá z kopca; gravitácia ťahá auto po svahu
- Dojazd je ladený tak, že ~10 L vystačí zhruba na 2 km — jazda má priestor
  na progres a nájdené diely sa stihnú prejaviť

### Povrch vetvy
Každá odbočka je z niečoho iného — **asfalt, popraskaný asfalt, betónové platne,
hlina, štrk alebo piesková stopa** (`RoadPaving`). Vidno to hneď: iná farba, iná
textúra (praskliny, škáry každé 4 m, zrno), a hlina/štrk/piesok sa jazdia po
vyjazdených koľajach. Stredové čiary sme zrušili — cesta má hovoriť materiálom.

Povrch nesie aj mierny grip (asfalt 1.00 → piesková stopa 0.88), takže
skratka nie je len iná farba. Naplaveniny (bahno, voda) sa rátajú navyše.

### Ťah, rýchlosť a stúpania
Gravitácia je arkádovo nafúknutá (`AIR_GRAVITY = 22`), takže **ťah motora
musí byť poriadne nad ňou**, inak auto nemá do kopca čím tlačiť. S pôvodným
`ACCEL = 9.5` a valivým odporom 1.8 sa štartovné auto na rovine ustálilo na
38 km/h a 25 % stúpanie ho zastavilo úplne.

Teraz `ACCEL = 14`, `COAST_DRAG = 1.15` a `TIRE_MU = 1.20`, takže o rýchlosti
rozhoduje výkon, nie preklz:

| Zostava | Rovina | 25 % stúpanie |
|---|---|---|
| Štart (Engine A, ojazdené gumy) | ~52 km/h | ~23 km/h |
| Engine B + dobré gumy | ~88 km/h | ~73 km/h |

### Výkon vs. grip
Základný motor je **Engine A (78 hp)** — s 2WD prenáša ťah len jedna náprava,
takže slabší motor sa na štarte iba prepaľoval a za pár sto metrov zožral gumy.

Keď kolesá preklzávajú, vodič uberie — ale **nikdy pod `TRACTION_EASE_FLOOR`
(72 %)** a pri rozjazde zo stojky vôbec. Bez tohto stropu vznikla špirála
(preklz → menej výkonu → pomalšie → stále preklz) a na nespevnenej vetve
sa nedal vyjsť ani mierny kopec.

Preklz sa neráta z prebytku momentu, ale z **pomeru ťahu ku gripu**:
do `TRACTION_SLACK` (1.3×) sa auto ešte chytí, nad tým sa pretáča. Preto
štartovné auto na asfalte zaberie, ale v bahne alebo s vypálenými gumami
sa točí — a 130 hp bez dobrých gúm je len dym.

**FWD má motor nad hnanou nápravou** (`REAR_BIAS_FWD = 0.32`), a prenos váhy
aj vplyv stúpania sa mu krátia (`FWD_TRANSFER_MUL`, `FWD_SLOPE_MUL`) — pohon
sa na štarte prideľuje náhodne, takže FWD nesmie byť výrazne horšia hra, takže predok
unesie ťah aj pri plnom plyne. Keď kolesá aj tak preklznú, vodič uberie
(`TRACTION_EASE_OFF`) — preklz sa ustáli tam, kde auto ešte ťahá, namiesto
nekonečného pálenia gúm.

Valivé opotrebenie berie obe gumy, **preklz len tú hnanú** (pri 4×4 obe,
brzdové zablokovanie tiež obe). Pri RWD sa teda zodiera zadok, pri FWD predok.

### Grip a stúpania
Gumy nedržia úmerne stavu — dezén sa zodiera pomaly, takže **45 % guma má
stále ~75 % gripu**; strmo padá až pod 30 % (`Car.treadFactor`). Vďaka tomu
opotrebené kolesá kopce spomalia, ale nezablokujú.

Do kopca sa časť váhy prenáša na zadnú nápravu (`SLOPE_TRANSFER`), takže RWD
ťahá hore lepšie a FWD horšie. Pri rozjazde zo stojky platí statické trenie
(`STATIC_GRIP_BONUS`, +30 % μ), aby sa dalo naštartovať aj v stúpaní.

`TERRAIN_MAX_STEEP` je zámerne pod tým, čo utiahnu slušné gumy — generátor
nesmie postaviť stenu, ktorá sa nedá vyjsť ani s najlepším autom. Čo grip
naozaj obmedzuje, je **prenos výkonu**: 130 hp na zodratých gumách sa prepáli,
na dobrých zaberie. Preto sa oplatí zbierať gumy aj vtedy, keď auto ide.

### Zima (neskorší progres)
Od **11 km** sa môžu objaviť zasnežené vetvy (`SNOW`, `PACKED_SNOW`) – šanca
rastie so vzdialenosťou. Sneh nie je len iná farba, je to iná hra:

- **Grip** rozhoduje zimná výbava. Letná guma má `snowGrip 0.45`, off-road 0.70,
  **zimná guma 1.0**, **reťaze** to celé násobia ×1.55. Bez ničoho sa auto
  na snehu len točí.
- **Reťaze na suchu prekážajú**: −12 % gripu, strop 43 km/h a rýchlejšie
  zodieranie gúm. Treba ich zložiť, keď sneh skončí.
- **Chlad**: motor sa nedostane na prevádzkovú teplotu (−26 °C od cieľovej),
  studený žerie viac paliva a batéria dáva menej.
- **Voda v chladiči zamrzne** a trhá motor (`FROZEN_COOLANT`) – čistota
  kvapaliny je zrazu životne dôležitá.
- Na snehu sa naplavuje **ľad** (`ICE`, grip 0.34) a **rozbrédnutý sneh**
  (`SLUSH`), nie bahno.

Zimná výbava padá do lootu už od **7 km**, teda skôr, než ju treba – hráč sa
má stihnúť pripraviť, nie ju zháňať už v snehu.

### Batoh a úložisko
Základný batoh má 16 slotov / 160 kg. Rozšíriť sa dá tromi vecami, ktoré
sa **sčítavajú** (sú to dva rôzne sloty na aute):

| Vec | Slot | Pridá | Cena |
|---|---|---|---|
| Hiking backpack | CARGO | +3 slotov, +20 kg | – |
| Boot crate | CARGO | +6 slotov, +45 kg | – |
| Roof rack | ROOF_RACK | +5 slotov, +40 kg | odpor vzduchu (−rýchlosť) |

Strešný nosič **vidno na aute** – lišty na nožičkách a bedne, ktorých pribúda
podľa toho, ako plný je batoh. Zložiť sa dá až vtedy, keď sa obsah zmestí aj
bez neho; inak hra povie „Empty it first" a nič sa nestratí.

### Prehodenie gúm
Pri gume v batohu sú dve tlačidlá — **→ FRONT** a **→ REAR**, takže si hráč
sám vyberie nápravu (porovnanie sa robí voči horšej z nich). A v paneli AUTO
je **SWAP TYRES**: prehodí prednú a zadnú gumu. Keď sa hnaná
náprava zodrala a druhá je ešte slušná, jazda sa dá predĺžiť aj bez nálezu.

### Počasie
Dážď (udalosť `RAIN`) je aj vidieť – šikmé šmuhy cez celý obraz, ktoré sa
nakláňajú podľa rýchlosti, plus studený závoj. V zime padá sneh: pomalé
hompáľajúce sa vločky.

### Prekážky na ceste
Okrem terénu ležia na vozovke **naplaveniny** — bahno, piesok, voda a štrk.
Nie sú to neviditeľné pasce: každá je vykreslená priamo na ceste vlastnou
farbou a textúrou (vlnky na vode, zrno v piesku, hrudy v bahne, kamienky
v štrku), takže ich vidno z diaľky, a HUD hlási `MUD IN 34 m`.

| Povrch | Grip | Valivý odpor |
|---|---|---|
| Tarmac | 1.00 | – |
| Gravel | 0.84 | mierny |
| Water | 0.72 | vysoký |
| Sand | 0.66 | vysoký |
| Mud | 0.55 | najvyšší |

Rozbitá cesta sype štrk, roklina drží vodu, skratky bývajú zaviate pieskom.
Na moste ani na hrebeni naplavenina nikdy nie je. Pri prejazde strieka spod
oboch kolies materiál v farbe povrchu.

### Budovy + loot
- Dom, garáž, benzínová stanica, autoservis
- Procedurálny loot podľa typu budovy, vzdialenosti a rarity

### Denný cyklus
- Čas plynie počas celej jazdy (celý cyklus ≈ 10 min), HUD ukazuje herné hodiny
- Obloha, kulisy aj budovy sa menia so svetlom (východ / poludnie / súmrak / noc)
- V noci treba **svetlomety** – bez nich je rýchlosť zastropovaná
- Svetlá berú z batérie; pri vypnutom motore ju vybijú a jazda skončí
- Alternátor dobíja batériu len počas behu motora

### Dlhodobý progres
Krivky náročnosti nemajú tvrdý strop — používajú `MathX.growth(d, softCap)`,
teda logaritmický rast: rýchly na začiatku, pomalý, ale nikdy nulový ďalej.
Terén, kvalita lootu, dĺžka úsekov, frekvencia udalostí aj sucho na pumpách
sa preto medzi 8. a 30. km stále menia.

**Opotrebenie ako motor neskorej hry.** Gumy, brzdy, pruženie aj motor sa
zodierajú samotnou jazdou (~25 % gúm na 20 km). Okolo 20–30 km sa hra prepne
z „zháňam lepšie diely" na „udržujem auto pojazdné" — náhradné diely majú
zmysel zbierať, aj keď je auto práve v poriadku.

**Depá každých 5 km.** Garantované miesto s plným stojanom a niekoľkými
poriadnymi dielmi; nad ním vlajka viditeľná z diaľky. Dáva jazde rytmus
a cieľ („ešte to dotiahnem k ďalšiemu depu"). Čím ďalej depo je, tým vyššia
šanca na diel tretieho stupňa — **Engine C (130 hp)**, **heavy-duty chladič**,
**110 L nádrž** — aby chase neskončil, keď je auto raz vybavené.

### Rázcestia
Vetva sa volí **za jazdy**. Asi 220 m pred odbočkou sa dolu objaví pás s vetvami
a odpočtom metrov, v scéne sa rozbehnú farebné stopy vetiev a pri ceste stojí
smerovka; vybraná vetva svieti, ostatné sa stlmia. Auto cez rázcestie prejde
bez zastavenia, bez teleportu a bez straty švihu.

Kto sa chce rozhodnúť v pokoji, môže pri odbočke zastaviť — vtedy sa otvorí
starý panel. Ak hráč nevolí vôbec, hra ho pošle do **najmenej rizikovej** vetvy
(hláška „No call made…"). Po voľbe sa dá pri odbočke normálne lootovať,
panel už neotravuje.

### Nečakané udalosti
Počas jazdy sa každých 26–58 s losuje udalosť. Čím ďalej si, tým vyššia šanca
na smolu — blízko štartu ťa hra nedrví.

| Smola | Šťastie |
|---|---|
| defekt gumy, kameň do chladiča | kanister pri ceste |
| únik paliva / chladiacej (beží v čase) | ošúpaný vrak s dielmi |
| misfire — motor stratí ťah, palivo ide ďalej | vietor v chrbte (−40 % spotreba) |
| prasknutý remeň — alternátor nenabíja | čistá cesta (−20 % spotreba) |
| dážď, blato, popadané konáre — horší záber a strop rýchlosti | rádio, stopy, srnka pri ceste |

Udalosti s trvaním sú v HUD ako modré štítky s odpočtom (`FUEL LEAK 23s`)
a ukladajú sa aj do rozohranej jazdy — reload ich nezruší.

### Keď auto zhasne
Dôjdené palivo, vybitá batéria ani prehriaty motor **jazdu automaticky
neukončia**. Auto zastane, motor zhasne a hra povie prečo — doliať sa dá
z batohu, batériu či motor vymeniť, prehriaty motor nechať vychladnúť.

Koniec príde až vtedy, keď z toho naozaj niet cesty von: v batohu nič, budova
nablízku nič, stojan prázdny a záchranná sieť vyčerpaná (`canRecoverFrom`).

### Uloženie jazdy
- Rozohraná jazda sa odloží do DataStore pri pauze, na križovatke a po štarte
  motora — zabitie appky ju už nezahodí
- Ukladá sa len trvalý stav (auto, batoh, budovy, priebeh); terén a úseky sa
  dopočítajú zo seedu, prechodná fyzika sa po obnove usadí sama
- Po obnove auto vždy stojí, nikdy sa nepokračuje v plnej jazde
- Formát je textový (`RunCodec`), verziovaný — poškodený alebo starý záznam
  sa ticho zahodí a hra začne novú jazdu

**Pri pridaní nového trvalého stavu auta ho treba doplniť do `CarState`
v `game/save/RunSnapshot.kt`, inak sa po obnove stratí.**

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
| Rázcestie | pás nad STOP asi 220 m pred odbočkou — ťukni vetvu a ideš ďalej bez zastavenia |
| Pri aute | ŠTART, INVENTÁR, AUTO, BUDOVA, JAZDIŤ |
| V budove | loot → ZOBRAŤ, na benzínke NATANKOVAŤ |
| Kedykoľvek | vpravo hore hodiny (+FPS), pod nimi zvislé ikony: svetlá, pauza, FPS |
| Pri aute | ŠTART / JAZDIŤ sú oddelené vpravo od INVENTÁR, AUTO a BUDOVA |
| Menu | POKRAČOVAŤ sa vráti do rozohranej jazdy, NOVÁ JAZDA začne od vraku |
| Pauza | CONTINUE / MENU, pod nimi RESTART RUN – zahodí jazdu a začne novú |
| Panel AUTO | všetkých 14 dielov naraz v mriežke → OPRAVIŤ / DEMONTOVAŤ |

HUD: vľavo prístrojovka (palivo, olej, chladenie, batéria, teplota, karoséria —
každý prúžok má v sebe tenkú linku čistoty kvapaliny), vpravo rýchlosť,
vzdialenosť, rekord, hodiny a voliteľné FPS. Pod tým sa objaví **pás výstrah**
(prehrievanie, málo paliva, špinavý olej, bez svetiel…), takže netreba lúštiť čísla.

Celé UI stojí na jednom dizajnovom systéme (`ui/theme/Design.kt`): jedna paleta,
jeden panel, jedny tlačidlá, jedna karta predmetu — v hre, v inventári,
v budove aj v menu.

Bočný 2.5D vizuál (HillRush depth projekcia): cesta má hĺbku, auto je extrudované zboku,
križovatky ukazujú rozvetvenie trate.

### Vizuál
- Procedurálna obloha s denným cyklom (gradient, slnko/mesiac, hviezdy, oblaky)
- Dve parallax vrstvy vzdialených kopcov + pás siluety z bitmapy
- Kulisy pri ceste: stromy, kríky, kamene, ploty, kontajnery, vraky
- Stĺpy elektrického vedenia s previsnutými drôtmi a míľniky každých 100 m
- Budovy podľa typu (dom, garáž, benzínka s prístreškom a stojanmi, autoservis),
  v noci svietia okná, vyrabované ostávajú tmavé
- Efekty auta: tieň, prach spod kolies, dym z výfuku, kužeľ svetlometov v noci,
  rozmazané kolesá pri rýchlosti
- **Točia sa len hnané kolesá.** Pri RWD sa pretáča zadné koleso a predné sa
len valí, pri FWD naopak, 4×4 točí obe — každá náprava má vlastný uhol
(`wheelSpinFrontDeg` / `wheelSpinRearDeg`) aj vlastný polomer podľa
namontovanej gumy. Dym, štrk a stopy idú spod **hnanej** nápravy; brzda
zablokuje obe.

**Preklz kolies**: zodraté gumy na plyn pretáčajú (koleso sa točí rýchlejšie
  než ide auto), spod neho ide dym a odletuje štrk, na ceste ostávajú stopy
  a autom myká; prudká brzda kolesá zablokuje a stopy sú súvislé
- Atmosféra: hmla nad horizontom, silueta lesa ako tretia parallax vrstva,
  tiene kulís na zemi, vinetácia a otrasy kamery na rozbitej ceste

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
