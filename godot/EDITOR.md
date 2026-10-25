# Ručné úpravy v Godote

Projekt otvor cez `project.godot` v tomto priečinku:

`C:\Users\Kubis\StudioProjects\EndlessDrive\godot`

Ak už máš Godot otvorený, potvrď **Reload / znovunačítanie zmenených scén**.
Ak sa nezobrazí výzva, zatvor kartu `main` a otvor ju znova zo súborového stromu.
Prípadné vlastné neuložené úpravy si najprv ulož zvlášť.

## 3D scéna, kamera a auto

Rozhranie variantu C nájdeš v `scenes/ui/expedition_hud.tscn` a v hlavnej
scéne pod `HUD/Root/ExpeditionHUD`. Otvor ho v pohľade **2D**. Prepínač
**Preview Equipment** na koreňovom uzle zobrazí vejárovú ponuku aj v editore.
Farby, ikony a štýly sú uložené na jednotlivých uzloch; adaptívne pozície
pre rôzne displeje počíta `scripts/expedition_hud.gd` vo funkcii `_layout`.
Staršie skryté uzly v `hud.tscn` zostávajú kvôli servisným a inventárnym väzbám.
V hre otvoríš výbavu ikonou náradia dole uprostred. **DOTYK · AUTO / ZAP / VYP**
prepína viditeľnosť pedálov; voľba sa zapamätá. Na telefóne používaj režim na šírku.

Navijak je upraviteľná scéna `scenes/winch_mount.tscn`, vložená do `Vehicle`.
Body na stromoch, skalách a stožiaroch majú skupinu `winch_anchor`.
Nové prístrešky, náhodné udalosti a zmiešané povrchy sú aj v generovaných
náhľadoch terénu. Ovládanie: **V** pohon 4×2/4×4, **B** uzávierka,
**G** pripojenie navijaka a **H** priťahovanie. Jazyk SK/EN zmeníš v nastaveniach.

Otvor `scenes/main.tscn` a hore zvoľ **3D**. Už obsahuje cestu, skaly,
vegetáciu, úvodnú dielňu, auto, svetlo a kameru aj bez spustenej hry.

- Označ `Vehicle` a stlač **F** na priblíženie pohľadu k autu.
- Označ `Camera3D` a zapni **Preview**, ak chceš pohľad hernej kamery.
- Polohu a `Size` tejto kamery môžeš upraviť. Hra z nich preberie odstup
  a priblíženie sledovacej kamery. Natočenie počas jazdy automaticky mieri na auto.
- `scenes/vehicle.tscn` obsahuje skutočné fyzické auto a jeho kolízny tvar.
  Aktuálny Jeep sa upravuje v `assets/models/jeep/jeep_cherokee.tscn`.
  Kolíziu nechaj zodpovedať rozmerom modelu.
- Poloha auta v `main` je referenčná; počas hry ju určuje štart/uložený postup.

**Dôležité:** `EditorTerrainPreview` a `StartCampPreview` sú označené náhľady.
Pri F5 sa odstránia, aby sa nezdvojili kolízie. Presúvanie kameňov v tomto
náhľade nemení generátor nekonečnej mapy.

## Piesková testovacia scéna

Otvor `scenes/sand_lab.tscn` a stlač **F6**. Je to samostatná plocha čistého
piesku s reálnym autom 4×4. Nemení uloženú expedíciu. V editore vidíš uložený
náhľad koľají z automatického jazdného testu; po F6 začínaš na čistom piesku.

- **WASD / šípky**: jazda.
- **R** alebo tlačidlo Vrátiť auto: návrat na štart so zachovaním koľají.
- **C**: vyčistiť piesok a vrátiť auto.
- **P**: zapnúť/vypnúť spray piesku.

Kreslené stopy sú odstránené z ovládania a vypnuté aj v hlavnej hre; ich
zásobník sa nealokuje. Spray je v laboratóriu predvolene vypnutý. Ryhy tvorí
samotná geometria vrátane kolízie: fyzická mriežka 25 cm, priehlbiny maximálne 20 cm
a vytlačené okraje maximálne 8,5 cm. Opakované prejazdy ich prehlbujú až po
tento limit. Dno a vytlačené okraje majú plynulý profil. Iba prejdené diely
majú jemnejšiu vizuálnu mriežku 12,5 cm; jej odchýlka od fyzickej kolízie je
obmedzená na 1,8 cm. Obnovujú sa iba dotknuté 8-metrové diely medzi snímkami.
Toto jemnejšie rozlíšenie zatiaľ používa laboratórium, nie celá nekonečná mapa.

Projekt používa vstavaný **Jolt Physics**. Po aktualizácii `project.godot`
reštartuj Godot, aby sa zmenil aj backend otvoreného editora. Testovacia scéna
používa výškovú kolíziu na 25 cm mriežke. Vizuálne zaoblenie zachováva výšku
v jej vrcholoch. Podvozok a štyri pôvodné kolízne pneumatiky zostávajú.
Počítadlo FPS sa zobrazuje v ovládaní testovacej scény.

`tools/godot.ps1 SandBench` spustí reprodukovateľné meranie státia, jazdy a
jazdy s prachom. Používa natívne vykresľovanie bez VSync/limitu FPS a pevný
simulačný krok 1/60 s, nie headless renderer. Výsledky sú v
`build/sand-lab-benchmark.json`; ide o porovnávací výkon na danom PC, nie
garanciu mobilných FPS. Nastavenie VSync mení iba proces benchmarku.

Meranie 25. 10. 2026 na RTX 3070 (Compatibility): približne 220 FPS pri jazde
bez sprayu a 209 FPS so sprayom, 95. percentil času snímky 15,74 ms so sprayom.
Výsledok je uložený v `build/sand-lab-benchmark-final-spray.json`.
Jemnejšia geometria je náročnejšia než predchádzajúca hrubá mriežka
(približne 238 FPS so starým prachom); mobil ešte nebol meraný.

Overenie: `tests/sand_lab_checks.gd` testuje jazdu, lokálnosť deformácie,
kolíziu a zachovanie vzhľadu starých častíc. Bez `--headless` uloží aj snímky
do `build/sand-lab-deformation.png` a `build/sand-lab-dust.png`. Voliteľný
argument `--save-preview` obnoví generovaný náhľad pre editor.

Nový pieskový spray platí aj v hlavnej hre: na koleso má najviac 192 drobných
3D zrniek, 24 väčších kúskov a 28 malých prachových častíc. Jemný prach používa
procedurálny shader `shaders/sand_powder.gdshader`, bez pôvodnej cloud textúry.
Častice nemajú vlastné kolízne telesá a nevrhajú tiene. Každé koleso prepína medzi samostatnými
skupinami častíc podľa povrchu. Už vypustený piesok zostane pieskom; prejazd
do vody, blata alebo snehu ho neprezafarbí.

## Deformácia a križovatky v hlavnej hre

Hlavná hra už používa rovnaký zaoblený profil ryhy a vytlačených okrajov pre
piesok, hlinu, trávnatý podklad, lesnú pôdu, íl, štrk aj sneh. Podklad sa ukladá
na 25 cm mriežke. Worker zahusťuje iba dotknuté miestne štvorce terénu;
vykreslenie a kolízia tam používajú rovnaké jemné trojuholníky. Zvyšok mapy
zostáva na pôvodnej mriežke. V skutočne vyrytej tráve a lesnej hrabanke sa
odhalí hlinený podklad; zafarbenie sleduje výšku fyzickej deformácie.
Uložené staré metrové koľaje sa plynulo prevedú.

Na udržiavanej nespevnenej ceste je limit ryhy polovičný oproti rovnakému
povrchu mimo cesty: piesok 14/28 cm, hlina 14/28 cm, štrk 8/16 cm,
trávnatý podklad 12/24 cm a sneh 18/36 cm. Limity sa dosahujú opakovanými
prejazdmi. Asfalt, betón, skala, ľad ani pevná mostovka sa neprekopávajú. Bahnitá prekážka
na ceste ruší jej pevnejšiu podporu: tvorí až 40 cm koľaj a v neskorších
nasýtených vreckách môže pri dlhom prešmykovaní zlyhať podložie až do 85 cm.
Pomôže vhodná pneumatika, 4×4, uzávierka a navijak (G pripojiť, H priťahovať).

Spray používa samostatné stabilné skupiny pre piesok, zeminu/blato, kamienky,
sneh a vodu. Mokré blato odhadzuje hrudky a netvorí suchý oblak. Častice
sú vopred pripravené, bez vlastných fyzikálnych telies a tieňov; ich shaderové
varianty sa vykreslia už pri príprave hry. Palety terénu, skál, vegetácie,
objektov, vody a prachu majú stlmenú sýtosť; pôvodné detailné mapy a mipmapy
zostávajú zachované.

Generátor strieda Y a X križovatky a serpentíny s reálnymi
otočkami do opačného smeru. Galériu otvor v `scenes/world/junctions_preview.tscn`;
v strome sú samostatné skupiny Y, X a Switchback. F5 používa tieto
tvary priamo v expedícii. Obe viditeľné vetvy zostávajú prejazdné aj po voľbe
a za križovatkou sa bezpečne napoja. Pri presune na druhý výjazd v rámci
križovatky sa aktualizuje aj uložená voľba. Voľba ovplyvní generovanie až vo vzdialenejšej
oblasti. Pri výmene budúcich dielov zostáva stará cesta až do pripravenia novej
kolízie vrátane mostovky.

`tests/surface_junction_checks.gd` overuje povrchy, ukladanie a zhodu detailného
vykreslenia s fyzickou kolíziou; `tests/junction_drive_checks.gd` fyzicky prejde
opačným X výjazdom po uloženej voľbe aj serpentíny.
`tests/main_soil_benchmark.gd` meria natívne vykresľovanie hlavnej hry na
1280 × 720 a zapisuje výsledky do `build/main-soil-benchmark.json`. Iba v tomto
benchmarku sú vypnuté kolízie náhodných objektov, aby jazdu pri meraní pôdy
neukončil strom; všetka scenéria sa ďalej vykresľuje. Samostatné jazdné testy
používajú skutočné kolízie objektov.

Staršie porovnávacie meranie hlavného terénu a sprayu 25. 10. 2026 na RTX 3070 (Compatibility,
1280 × 720): približne 194 FPS na pieskovej ceste, 127 FPS v hlbokom piesku,
190 FPS na trávnatom podklade a 191 FPS na snehu. Čas snímky na 95. percentile
bol 13,5–16,1 ms, najdlhšia snímka 27,8 ms. Ide o porovnávacie meranie uvedených
situácií na PC; mobilný výkon zatiaľ nebol meraný.

## Plynulosť a mierka materiálov

Aktuálna verzia presúva aj samotné otláčanie kolies a výpočet sadania pôdy
na súkromný číselný snapshot vo workeri. Každých približne 80 ms sa obnovia
iba dotknuté 8 m pásy; susedné pásy sa publikujú spolu pred fyzikou auta,
aby nevznikla dočasná kolízna hrana. Obnova starých koľají má rozpočet dvoch
pásov na dávku. Kamera sleduje sklon pôvodného podkladu s vyhladením, takže
čerstvo prekopaná pôda nemení výšku celej kamery.

Textúry podkladu, skál a objektov majú približne dvojnásobnú svetovú mierku.
Jemný kontrast a reliéf sa tlmia podľa veľkosti svetového priestoru na pixel;
z predvolenej hernej kamery tak netvoria husté zrnenie. Mapy s vysokým
rozlíšením a mipmapami zostávajú zachované. Zdieľané shadery sa prejavia aj
v uložených náhľadoch v editore.

Pravidelné automatické ukladanie serializuje koľaje aj JSON a zapisuje súbor
v samostatnom workeri. Ručné uloženie, ukončenie a reštart najprv dokončia
predchádzajúci zápis; ručné uloženie zahrnie aj posledné čakajúce deformácie.
Pri naplnenom limite 65 536 vzoriek trvalo pôvodné synchrónne uloženie na
meranom PC približne 236 ms; aktuálne odovzdanie snapshotu trvalo približne 9–14 ms.

Natívne meranie v aktívnom okne na RTX 3070, 1280 × 720, skutočná herná kamera
s veľkosťou 40: piesok mimo cesty približne 177 FPS, 95. percentil 12,84 ms,
99. percentil 16,85 ms, maximum 21,51 ms počas 8 s. Špička publikovania pôdy
bola 6,12 ms oproti pôvodným približne 11 ms. Je to krátky PC scenár,
nie záruka výkonu celej expedície alebo telefónu. Výsledok je v
`build/main-soil-benchmark-pacing-visible.json`; benchmark podporuje
`--live --vsync --foreground --qa-delay --case=sand_offroad --label=...`.
Pri meraní nespúšťaj súbežne ďalšie testy.

`tests/frame_pacing_checks.gd` kontroluje vyhladenie kamery, asynchrónne
odtláčanie pôdy, publikovanie geometrie, FIFO pri plnej histórii a uloženie
nemenného snapshotu počas ďalšieho vykresľovania.

## Aktuálny terén, textúry a tunely v editore

V `main.tscn` označ **EditorTerrainPreview**. Náhľad už používa aktuálne
materiály vrátane 2K jemného reliéfu, takže zmeny zdieľaných textúr a shaderov
vidíš aj bez F5. V Inspectore vieš nastaviť **Preview Seed** a
**Preview Distance** a stlačiť **Obnoviť terén zo skriptov**. Výpočet beží
v samostatnom procese; po jeho dokončení sa vymení iba generovaná geometria.
Nový seed náhľadu nemení tvoj uložený postup ani seed novej hry.

Samostatné `scenes/world/bridge_preview.tscn`, `rail_preview.tscn` a
`tunnel_preview.tscn` zobrazujú rovnaké mosty, priecestia a vyplnené tunelové
masívy ako generátor hry. Po otvorení označ **GeneratedTerrain** a stlač **F**.
Nový `scenes/world/alpine_preview.tscn` ukazuje prerobený horský úsek:
štrkovú cestu v údolí, pozvoľné stúpania a sneh predovšetkým na svahoch.
Tieto náhľady tiež majú tlačidlo obnovy. Všetkých päť možno obnoviť naraz cez
`tools/godot.ps1 RefreshPreview`. Generované `.scn` sú uložené v
`scenes/world/generated/`; ručne upravené UI, kameru, auto a budovy tento nástroj
neprepisuje. Náhľady mosta/priecestia/tunela sú vzorky, ich presúvanie neprepíše
algoritmus stavby počas hry.

`scenes/world/deformation_preview.tscn` je samostatná ukážka vyjazdených
koľají a vytlačených okrajov vytvorených produkčným generátorom. Spusť ju cez
**F6**; `PreviewCamera` ukazuje detail terénu a `DustSpritePreview` nový prach.
Náhľad možno znovu vytvoriť skriptom `tests/build_deformation_preview.gd`.
Nemení rozjazdenú cestu ani uložený postup. Štandardná hra používa rovnaké
materiály a deformáciu automaticky.

Prach a odlietavajúce zrnká majú vlastné, skutočne používané scény
`assets/effects/wheel_dust.tscn` a `wheel_grit.tscn`. Otvor ich v 3D editore,
označ emitter a dočasne zapni **Emitting**, aby si videl simuláciu. **Amount**,
**Lifetime**, mesh, materiál, krivka veľkosti a priehľadnosti sa preberajú do hry.
Farbu, smer, rýchlosť a zapínanie za jazdy riadi kontakt kolesa s povrchom;
pri vode sa prach vypne a zrnká dostanú materiál kvapiek. Pred uložením náhľadu
môže zostať Emitting vypnuté; hra ho zapína podľa kontaktov.

Cesta drží šírku aj v zákrutách a má odlíšenú krajnicu. Vodiace stĺpiky
upravuj v `assets/models/road_delineator.tscn`. Voliteľné bočné stopy majú
skutočné ryhy hlboké približne 45–65 cm; hlavná cesta si ponecháva plytšie
15–24 cm koľaje. Predgenerované ryhy sú viditeľné aj v obnovenom náhľade.
Vjazdy do budov, križovatky a konštrukcie zostávajú priechodné.

Sivá hmla je vypnutá v uloženej scéne aj počas počasia. Nové textúrované
materiály dreva, omietky, kovu a vegetácie sú priamo v zdrojových modeloch;
v materiáli môžeš upraviť `tint`, `surface_kind` a drsnosť. Podrobnosti a zdroj
atlasu sú v `assets/textures/WORLD_MATERIALS.md`. Náhľady sú obnovené aj s nimi.
Výfuk má samostatnú editovateľnú scénu `assets/effects/exhaust.tscn`.

## Budovy a skutočná úvodná lokalita

Upravuj zdrojové scény, nie vetvy s názvom `Preview`:

- `scenes/world/start_camp.tscn` — rozmiestnenie dielne a rekvizít úvodnej zastávky.
  Táto scéna sa naozaj používa pri generovaní prvej zastávky v hre.
- `assets/models/stops/workshop.tscn` — dielňa.
- `assets/models/stops/repair.tscn` — autoservis.
- `assets/models/stops/paint.tscn` — lakovňa.
- `assets/models/stops/gas.tscn` — čerpacia stanica.
- `assets/models/stops/home.tscn` — dom.
- Ostatné skaly, kaktusy, ploty a stĺpy sú v `assets/models/`.

Všetky tieto modely používa aj bežiaca hra. Zmena rozmerov, materiálov
alebo vnútorného rozmiestnenia sa prejaví pri novom spustení/načítaní zastávky.
Udržuj voľný vjazd a približne pôvodný pôdorys: generátor rezervuje okolo
zastávok určitý priestor, ale nevie vyhodnotiť ľubovoľne veľkú budovu.
Pri presunutí steny posuň aj príslušný `StaticBody3D` / `CollisionShape3D`.
Nepridávaj rekvizity do prejazdu. Výška a miesto celej zastávky na trase zostávajú
riadené terénom; vo vnútri scény sú polohy tvoje.

Pri vnorenom modeli dvojklikni na jeho ikonu scény v strome, čím otvoríš zdroj.
Prípadne použi **Editable Children**, ak chceš upraviť iba danú inštanciu.

## Kolesá a podvozok v mäkkom teréne

V `scenes/vehicle.tscn` sú štyri pevné kontakty `Contact_F_L`, `Contact_F_R`,
`Contact_R_L`, `Contact_R_R` a samostatný `ChassisCollision`. Kolesá nesú auto
na bežnom povrchu; pri hlbokých koľajach sa môže oprieť o podvozok a stratiť
záber. Tieto kolízne tvary môžeš prezerať v 3D editore. Pri zmene ich rozmerov
zachovaj zhodu s viditeľnými kolesami: samotný posun modelu nemení kontakt fyziky.
Aktuálny Jeep má rozchod približne 1,606 m, rázvor 2,96 m a polomer kolies 0,44 m.
Hitbox karosérie je konvexný obal modelu vrátane doplnenej spodnej ochrannej platne,
nie pôvodná veľká kapsula. Kontakty kolies sú valce so šírkou 0,286 m.
V scéne modelu môžeš presúvať aj `HeadlightL/R` a `BrakeLampL/R`; svetlá ich používajú
pri jazde. Uzly `Wheel_FL/FR/RL/RR` sú osi riadenia a pruženia; ich geometria je
vycentrovaná na stred nápravy. Nemeň ich názvy bez úpravy obsluhy kolies.
Procedurálna pôda sa pod zaťažením mení až počas hrania, takže jej konkrétne
vyjazdené jamy neuvidíš v statickom náhľade editora. Hra ich ukladá s cestou.

## UI — jazda, lootovanie, servis a pauza

1. Otvor `scenes/ui/hud.tscn` a hore zvoľ **2D**.
2. Označ koreň `HUD` a v **Inspector** nájdi **Editor View**.
3. Vyber **Jazda**, **Loot**, **Vozidlo** alebo **Pauza**.
4. Rozbaľ `Root` a príslušný panel. Jeho tlačidlá, popisy, indikátory
   a schéma auta sú už uložené uzly, nie iba objekty vytvárané počas hrania.

Prepínač iba mení náhľad v editore. Nespúšťa hru, nenačítava ani neukladá
tvoj postup. Hra používa tú istú scénu a ponechá tvoje rozloženie.

Palubný panel má režim D/R/N a samostatný `Root/RoadAssist`. Ten pomáha
nájsť bezprostrednú cestu a smer zákruty; budovy dopredu neodhaľuje. Pri
interakcii s budovou ustúpi panelu prehľadávania. Tlačidlo svetiel ukazuje
aj stav prepnutia klávesom L. Panel zásob používa samostatné farby kvapalín.

Rýchlomer je natívny uzol `Root/PanelContainer4/HBoxContainer/Instrument` so
skriptom `dashboard_dial.gd`. Vieš meniť jeho veľkosť a pozíciu v 2D editore;
digitálna rýchlosť a jednotka sú samostatné Label uzly vo vnútri. Ikony zásob
sú ostré SVG v `assets/ui/`; plyn má vlastný výrazný štýl tlačidla.

Voľne umiestnené prvky (napríklad pedále) presuň myšou alebo cez
**Layout → Transform / offsets / anchors**. Vnútri `VBoxContainer`,
`HBoxContainer` a `GridContainer` poradie a pozíciu riadi kontajner:
presuň uzol vyššie/nižšie v strome, zmeň **Custom Minimum Size**, **Size Flags**
alebo **Theme Overrides → Constants → Separation**. Ťahanie samotného dieťaťa
kontajnera Godot pri ďalšom rozložení vráti späť.

Skutočné rýchlosti, stav dielov, text porovnania, zoznam nálezov a obsah kufra
sa stále dopĺňajú podľa hry. Loot zoznam preto v editore nemusí obsahovať riadky:
rám okna, kategórie, bočný prehľad a tlačidlá sa upravujú priamo tu.
Schéma auta sa kreslí aj v editore; jej veľkosť je editovateľná.

## Hlavné menu a jeho obrazovky

Otvor `scenes/ui/main_menu.tscn`, zvoľ **2D**, označ `MainMenu` a nastav
**Editor View**: Menu, Nastavenia, Ako hrať, Auto, Nová jazda, Ukončenie
alebo Vylepšenia. Pod `Root/Stage` nájdeš hlavný obsah a všetky podpanely.
Pri prepínaní za behu sa už nemažú a nevytvárajú znova.

Celý `Stage` sa prispôsobuje rozlíšeniu; upravuj prvky v jeho vnútri,
nie jeho automatický posun a mierku. Predvolený návrh je 1440 × 810.

## Uloženie a overenie

Ulož scénu cez **Ctrl+S**, potom stlač **F5** a over výsledok v hre.
Menu a HUD otváraj priamo cez ich vlastné `.tscn`, nie cez vzdialený strom
**Remote** spustenej hry. Úpravy v Remote sa do zdrojových scén neukladajú.

Uzly môžeš premenovať a presúvať v rámci príslušnej UI scény; naviazanie dát
nespolieha na ich pevné cesty. Zachovaj však metadáta `layout_bindings` a
prepojenia v karte **Signals**. Mazanie potrebných uzlov alebo ich presun do
inej samostatnej scény si vyžaduje aj zmenu obsluhy.

Bežné spustenie ani otvorenie editora nič z toho neregeneruje a neprepisuje.
Starý `BuildArt` je výslovný nástroj na obnovu pôvodných modelov — nepúšťaj ho,
ak si v nich už robil vlastné zmeny. Migračný nástroj `tests/build_editor_scenes.gd`
existujúce scény odmieta prepísať; na bežnú prácu ho nepotrebuješ.

## Horské výstupy, brody a nové ovládanie

Ručnú brzdu drž cez **medzerník** alebo tlačidlo **(P)** nad brzdovým pedálom.
Zruší tempomat, zabrzdí zadnú nápravu a nezaradí spiatočku. **L** prepína
vypnuté / stretávacie / diaľkové svetlá, **K** prepína diaľkové priamo.
Spiatočka zapína biele zadné svetlá s osvetlením zeme. Ich šošovky, svetlá
aj skrytý model šnorchla upravuj v `scenes/vehicle_equipment.tscn`, ktorý je
pripojený pod Visual v `scenes/vehicle.tscn`.

Kruhové objazdy boli odstránené. Bežné križovatky sú približne každé tri
regióny a budovy približne každých 1,9 km; úvodná dielňa zostáva na mieste.
Približne od tretieho kilometra sa občas objaví horský výstup so štyrmi
vracákmi, prevýšením približne 55–67 m a následným zostupom. Cesta sa naozaj
vracia do opačného smeru a má vlastnú súvislú výšku podľa prejdenej trasy.
Smer vracákov a výška sa menia podľa seedu. Na hrebeni je malé táborisko so
zásobami. Náhľad prvých vracákov otvor v `scenes/world/mountain_preview.tscn`.

Plytké predjazdené koľaje sú fyzickou súčasťou nespevnených ciest; nealokujú
ďalšie dekoratívne stopy. Bahno sa tiahne po zákrute v úsekoch približne
64–116 m. Neskoršie brody majú aj vrecká hlbšie než meter a plytší okraj.
Voda nad **80 cm** môže postupne zatopiť sériové nasávanie a zastaviť motor.
Šnorchel kúpiš za **14 šrotu** pri dielni/servise cez **Vozidlo → Kufor**;
chráni nasávanie do **1,85 m**. Zatopené auto treba vytiahnuť navijakom,
potom tam istým spôsobom vysušiť nasávanie za **2 šrotu**. Poškodenie motora
môže potrebovať samostatnú opravu. Ani šnorchel neochráni auto v ľubovoľne
hlbokej vode.

Piesočné fronty sú miestne a ohraničené. Môžu prichádzať spredu, zboku aj
zozadu, trvajú približne 65–100 sekúnd a potom nasleduje najmenej 280 sekúnd
bez novej búrky. Dá sa cez ne prejsť, vyjsť z nich, schovať sa alebo počkať.
Teplé prachové zahmlenie sa zapína iba vo vnútri frontu a plynulo zmizne po
jeho opustení. Časovanie sa ukladá spolu s hrou. Nové svetelné kombinácie aj
prachové zahmlenie sa pripravujú pri štarte; kombinácie skutočných živých
meshov sa navyše krátko vykreslia pod úvodným menu. Prvé zapnutie svetiel
po tejto príprave malo v samostatnom PC teste špičku približne 21 ms
oproti približne 53 ms bez nej.

HUD podľa referencie je uložený v `scenes/ui/expedition_hud.tscn`: spojené
šípky, samostatné pedále, vejár, kompaktné zásoby a červené varovanie.
Pod vejárikom sú malé ikony motora, vozidla, tempomatu a režimu ovládania.
Rozloženie ostáva upraviteľné v editore a podporuje súčasné dotyky.

Overenie: `tests/adventure_system_checks.gd`, `mountain_drive_checks.gd`,
`hud_c_checks.gd`, `stop_placement_checks.gd` a existujúce jazdné testy.
Kompletný fyzický prejazd horskej trasy zvládlo sériové 4×4 s prevýšením
60,9 m, viac než 170 m pohybu späť v osi X a bez záchranných teleportov vo V3.

## Priecestie, osadenie vegetácie a voda

Priecestie má zarovnaný štrkový násyp a výplň medzi koľajnicami. Podvaly
sa už neprekrývajú s povrchom terénu; pevná výplň sa nedá vyryť kolesami.
Aktuálny náhľad je v `scenes/world/rail_preview.tscn`.

Vegetácia v serpentínach sa rozmiestňuje podľa celého terénneho koridoru
a zapúšťa do skutočných trojuholníkov vykreslenej zeme. Príliš strmé miesta
sa preskočia. Zoskupené modely zachovávajú nastavenie vrhania tieňov.

Brody majú modelovaný breh a mokrý prechod do cesty. Priehľadnosť vody
sa riadi hĺbkou nad skutočným dnom a pri brehu plynulo mizne. Geometria
vody obsahuje hĺbku v UV.x; pri zmene generovania zachovaj tento údaj.
Fyzická hĺbka úvodného brodu zostáva 62 cm.

## Materiály a symboly prístrojovej dosky

Automobilové symboly sú samostatné SVG v `assets/ui/`, importované vo
dvojnásobnej mierke. Stretávacie a diaľkové svetlá používajú rozdielne
symboly a zelenú/modrú kontrolku. HUD aj servis používajú rovnaké zdroje.
Vektorové povrchy tlačidiel a panelov nájdeš v `assets/ui/skins/`; spoločné
nastavenie deväťdielneho škálovania je v `scripts/hud_skin.gd`.

`tools/build_ui_assets.py` je výslovný generátor týchto symbolov a povrchov;
prepíše ich, preto ho po ručných úpravách SVG nespúšťaj automaticky.
Rozloženie ostáva v `scenes/ui/expedition_hud.tscn`. Kontroly
`tests/dashboard_icon_checks.gd` overujú rozmery ikon a popisov na PC aj
telefóne; `tests/grounding_water_checks.gd` overuje brehy, podvaly a korene
vegetácie pri dvoch horských seedoch.

## Krajina a viditeľnosť vo V3

Nové uložené náhľady sú `scenes/world/canyon_preview.tscn`,
`forest_preview.tscn` a `swamp_preview.tscn`. Používajú rovnaký generátor
ako hra; ich tlačidlo **Obnoviť terén zo skriptov** mení iba príslušný
generovaný podklad. Kaňon má súvislé vrstvené steny a terasy. Les je hustejší,
mokrade majú bočné vodné plochy, korene a trstinu. Stromy zdieľajú geometriu
aj materiály, takže zvýšená hustota neznamená samostatné vykresľovanie
každého konára.

`scenes/vehicle_visibility.tscn` je pripojená pod vozidlom. Obrys zdieľa
skutočné meshe auta a kolies. Aktivuje sa pri zakrytí a hĺbkový shader potom
zvýrazní len skryté pixely; viditeľné časti auta nemení. Farbu upravuj v
`shaders/vehicle_outline.gdshader`. Nepridáva svetlá, tiene ani ďalší viewport.
