# Stabilizácia podľa auditu — 26. 10. 2026

Aktívny projekt je `godot/project.godot`. Táto iterácia zavádza prioritné
stabilizačné zmeny z auditu. Nie je tvrdením, že všetkých dvadsať odporúčaní
alebo všetky kombinácie sveta a zariadení sú už uzavreté.

## Vykonané zmeny

### Generovanie a výkon (A01, A02, A07, A09, A17)

- Aj jednotlivé vizuálne pásy terénu sa publikujú cez časovo obmedzený rad
  úloh. Predtým sa všetky vytvárali počas jedného vytvorenia chunku.
- Výpočet ďalšieho chunku sa môže prekrývať s publikovaním dekorácií.
  Rozpracovaná dekorácia už nebráni príprave budúcej cesty.
- Výber polôh plotov a vedení je súčasťou rozpočtovaných úloh. Každá strana
  hranice mapy má samostatnú úlohu. Vedenia zdieľajú geometriu a inštancovanie.
- Nová deformácia sa nepublikuje do nehotového renderu/kolízie chunku.
  Fyzický povrch a geometria zostávajú spolu; nemení sa kolízna maska auta.
- Budúci nedotknutý chunk nekopíruje celú historickú mapu koľají. Konzervatívne
  hranice poľa nedovolia vynechať existujúcu deformáciu. FIFO snapshoty navyše
  nekopírujú už vyradený začiatok histórie.
- Pôvodný veľký import navijaka nahrádza natívny `scenes/winch_mount.tscn`
  s upraviteľným vývodom `CableOutlet`. Ťahanie a kolízie lana zostávajú aktívne.
- V menu sú uložené voľby **Úsporná / Vyvážená / Vysoká** a **30 / 60 FPS /
  bez limitu**. Android bez uložených preferencií začína vyváženým režimom
  s limitom 30 FPS; počítač vysokým režimom pri 60 FPS.
- Režimy menia tiene, počty častíc a rozlíšenie 3D. UI zostáva v plnom
  rozlíšení. Fyzika, loot a hranice mapy sa kvalitou nemenia.
- Terén má samostatne kompilované shaderové varianty so spoločným zdrojom
  `shaders/desert_surface_common.gdshaderinc`. Vysoký zachováva detaily;
  vyvážený ponecháva fotografické albedo, ale vynecháva mikroreliéf a jemný
  procedurálny šum; úsporný používa jednoduchšiu paletu. Nepotrebná veľká
  mikrotextúra sa v nižších režimoch neviaže na terén.
- Predkreslenie pokrýva aj pohľady pri vstupe/výstupe z tunela a vybranú
  kvalitu. Po zmene kvality v menu sa pripraví aj nová konfigurácia.

Časový rozpočet nie je tvrdá záruka celej snímky: jedna nepreemptívna úloha,
ovládač alebo prvá kompilácia shaderu ho môžu prekročiť. Kopírovanie plného
snapshotu pri automatickom uložení tiež stále má cenu na hlavnom vlákne;
serializácia a zápis zostávajú na súkromnom vlákne.

### Jazda, pôda a ekonomika (A03, A10–A12)

- Nasýtená hlboká pôda má odpor pri zlyhaní nosnosti ešte pred vznikom hlbokej
  koľaje. Slabé auto už nemá bez odporu kĺzať po povrchu rašeliny. Obyčajný
  piesok týmto dodatočným odporom nie je ovplyvnený.
- Tempomat nemá samostatnú 50 % prirážku k palivu. Pri rovnakej rýchlosti,
  plyne a konfigurácii stojí jazda rovnako ako ručné ovládanie.
- `odometer` zaznamenáva skutočný vodorovný pohyb auta vrátane návratov.
  `distance` zostáva dosahom trasy pre rekord/progres. Záchranný presun ani
  posun počiatku nepridáva kilometre. Staré uloženia sa migrujú cez dostupnú
  historickú hodnotu `wear_distance`.
- Jazdný HUD ukazuje prejdené kilometre; jeho popis rozlišuje dosah trasy.

### Búrka, diagnostika a loot (A05, A06, A13)

- Búrka sa odloží bez načítaného dostupného prístrešku alebo s nepojazdným
  autom. Kontroluje sa voda a sklon prístupu, odhad dojazdu a rezerva času.
- Výstraha trvá 30–120 sekúnd podľa prístupu. Ak by ani maximum nestačilo,
  naháňačka sa nespustí. Vybraný platný úkryt sa nemení každú sekundu len
  preto, že sa iný stal bližším. Zaniknutý cieľ sa znovu overuje.
- Výstraha a strecha skutočne blokujú poškodzovanie aj pri doznievajúcej
  prašnosti. Trvanie aktívnej búrky sa nevyčerpá počas prípravy. Prach pri
  odložení alebo obnovenej výstrahe ustupuje plynulo.
- HUD vysvetľuje ručnú brzdu, brzdenie/cúvanie, palivo, motor, batériu,
  zapadnutie, opretý podvozok, prekážku a vodu. Radí dostupnú reakciu namiesto
  univerzálnej správy o zastavení. Pri búrke ukazuje zostávajúcu výstrahu.
- Loot a kufor dopĺňa číselné porovnanie účinku bŕzd, dostupného ťahu a
  priľnavosti pneumatík. Predikcia brzdenia berie do úvahy aj aktuálne
  pneumatiky a hmotnosť. Je označená ako suchý asfalt pri 72 km/h.
- Nové popisy a voľby majú anglické preklady. Grafické voľby sú natívne
  upraviteľné uzly v `scenes/ui/main_menu.tscn`. Tlačidlo obnovenia nastavení
  sa po rozšírení panelu overuje aj proti hranici obrazovky.

### Modely a export (A08, A09, A16, A20)

- Dva samostatné vrcholy dostali asymetrické ramená a členitejšiu siluetu pri
  rovnakom počte vrcholov. Textúry zostali zachované. Generátor má prepínač
  `--only-peaks`, aby neprepisoval ostatné ručne upraviteľné modely.
- Export vylučuje ukážkové scény, generované editorové cache, pôvodný GLB
  Jeepu a nepoužívaný import navijaka vrátane jeho odvodených textúr.
  Aktívny Jeep používa pripravený natívny rig a jeho skutočné závislosti.
- **Android Preview** zachováva podporu emulátora; **Android Device** obsahuje
  iba ARM64 pre telefóny. Nejde o komerčné vydanie ani test na zariadení.
- Pridaný je inventár `assets/ASSET_LICENSES.md`; atribúcia Jeepu sa vkladá
  aj do exportu. CC BY-NC-SA 4.0 naďalej vyžaduje vhodné oprávnenie alebo
  náhradu pred komerčným použitím. Žiadne nové práva k Jeepu sa tým nezískali.
- Koreňový README jednoznačne označuje Godot ako aktívny projekt a pôvodný
  Android/2D projekt ako referenčný archív. Archív sa pri stabilizácii nemaže.

## Overovanie a opravené kontrakty (A04)

`tools/godot.ps1 Test` izoluje používateľské dáta, nastavuje časový limit
každej sade, kontroluje chyby v logu a pokračuje ďalšími sadami aj po
zlyhaní. Na konci nahlási všetky zlyhania. Interaktívny editor/hra sa otvárajú
normálne; testovacie pomocné procesy zostávajú skryté.

- Hlavná sada: **127 kontrol bez zlyhania**.
- Fyzická jazda: **48 kontrol bez zlyhania**. Prejde dosah 4,6 km, približne
  5,26 km skutočnej dráhy vrátane serpentín, bez odlepenia pomocným presunom.
  Jazda sa overuje aj cez vodu, sneh, stúpanie, brzdy a skutočné prekážky.
- Horská skúška prešla oboma spätnými úsekmi X, nastúpala približne 61 m
  a nepoužila záchranný presun.
- Mäkká pôda: **12 kontrol bez zlyhania**; telo sa v skúške reálne znížilo
  približne o 34 cm a podvozok niesol auto nad hlbokými kanálmi.
- Kontroly búrky/navijaka, stability, povrchov, uloženia, UI, lokalizácie,
  modelov, pneumatík a bŕzd prešli príslušnými spustenými sadami.
- Nová regresná sada prejde aj tempomat, migráciu kilometrov, presun auta,
  diagnostiku, zníženie/obnovu grafiky a odloženie búrky bez úkrytu.
  Jej posledný beh má **21 kontrol bez zlyhania** vrátane odmietnutia
  kotiev navijaka z iného 3D sveta pomocného predkreslenia.
- Generované editorové náhľady terénu, mosta a hôr sú obnovené bez prepisu
  ručne upraviteľných obalových scén. Finálny obrázok nastavení bol aj
  vizuálne skontrolovaný; všetky voľby a spodné tlačidlo sa zmestia do panelu.
- Tiché vykresľovacie skúšky nespúšťajú zvuk do ovládača Dummy. Posledná
  jazdná skúška už nehlási predchádzajúci únik objektov prehrávania zvuku.

Niektoré pôvodné červené výsledky boli chybami skúšobného prostredia:
test banku používal procedurálnu pôdu/dekorácie, skúška pôdy strácala vlastný
materiál pri tvorbe snapshotu, jazdný ovládač ignoroval úseky serpentín
s klesajúcim X a brodenie predpokladalo vodu na každom type križovatky.
Tieto prípady sú opravené explicitne, nie odstránením fyzikálnych kontrol.
Kontrola HUD používa výslednú viditeľnosť v strome a plánovací limit zastávok
vyplýva zo skutočnej veľkosti okresu a jeho jitteru.

## Výkonové dôkazy a ich hranice

Kontrolované porovnanie rovnakej stojacej tunelovej scény na Intel Graphics,
OpenGL Compatibility, po zahriatí, bez VSync/limitu FPS, dva priechody:

- Vysoká: medián 5,34–5,37 ms, p99 19,16–19,88 ms; 369 volaní.
- Vyvážená: medián 4,68–4,82 ms, p99 17,98–18,52 ms; 369 volaní.
- Úsporná: medián 3,57–3,61 ms, p99 12,95–13,15 ms; 134 volaní.

Zdroj: `build/audit-fixes/quality-comparison.log`. Ide o intervaly skutočných
snímok tej istej scény, nie izolované GPU časovače, dlhú jazdu alebo telefón.
Zmena režimu zahŕňa celý balík nastavení, nie iba shader.

Pôvodný pohybový benchmark čakal na fyzický aj procesový takt a vynechával
medziľahlé vykreslené snímky. Teraz meria každý renderovací rámec a pohyb
odvodzuje od skutočného času pri 36 m/s. Staré a nové percentily preto nie sú
priamo porovnateľné. Hranice aktualizácie hry meria osobitne.
Logy vzniknuté súbežne s exportom nie sú kontrolovaným porovnaním výkonu.

Pri samostatnej fyzickej jazde s vyváženou grafikou, limitom 30 FPS a
predkreslením používaným hrou bol medián **33,57 ms**, p95 **58,59 ms**,
p99 **92,40 ms** a maximum **127,66 ms** na snímku. Auto prešlo 347,1 m;
maximum publikovania streamovaného terénu bolo 3,49 ms a aktualizácie
vyjazdenej pôdy 17,14 ms. Zdroj:
`build/audit-fixes/physical-render-primed-final.log`.
**Úplná plynulosť teda stále nie je vyriešená**, ani pri limite 30 FPS.
Krátka stojaca scéna vyššie nepredstavuje dôkaz plynulej jazdy. Finálna
úprava izolácie oblohy pomocného predkreslenia prešla zostavením exportu,
ale po nej už neprebehol nový výkonový benchmark.

## Spustenie

Otvoriť v Godote: `godot/project.godot`, potom F5.

Vybrané kontroly v PowerShelli:

```powershell
./tools/godot.ps1 Test -Suites audit_regression_checks,frame_pacing_checks,recovery_system_checks,menu_ui_checks
./tools/godot.ps1 Test -Suites driving_regression -TimeoutSeconds 600
./tools/godot.ps1 Android -ExportPreset 'Android Device'
./tools/godot.ps1 BuildPeaks
```

APK pre telefón: `godot/export/endless-drive-arm64.apk`. Debug export bol
zostavený a overený podpisovým nástrojom. Obsah balíka sa kontroluje aj cez
archív: žiadne textúry starého navijaka, žiadne editorové galérie a žiadna
knižnica x86; atribúcia aj všetky tri varianty shaderu terénu a ich spoločný
zdroj sú prítomné. Balík po stabilizačnej etape mal 74 894 965 bajtov;
po nasledujúcom doplnení vegetácie má 76 171 278 bajtov (približne 76 MB).

## Zostávajúce práce

- Overiť studenú grafickú cache, dlhšiu plynulosť vo všetkých biomoch,
  automatické uloženie s plným poľom a skutočný Android vrátane teplôt.
  Táto iterácia nesľubuje stabilných 60 FPS v každej situácii.
- GPU testy pri vypnutí na tomto rendererovom prostredí hlásia dve uniknuté
  textúry po 349 524 bajtoch. Nie je tým dokázaný rast pamäte počas hrania;
  hlásenie však zostáva otvorenou položkou pre kontrolu renderera/ovládača.
- Obsahové zákazky, nová sieť ciest so slučkami, širší zvukový systém,
  kompletná prestavba zodpovedností veľkých skriptov a automatizácia na
  vzdialenom CI zostávajú samostatnými etapami.
- Nie je vykonaná úplná fyzická jazda 120 km. Simulácia 64 seedov je
  plánovacou kontrolou zásob, nie dôkazom prejazdnosti celej takej trasy.
- Komerčné oprávnenie k modelu Jeepu sa musí vyriešiť pred monetizáciou.

Technický podklad pre škálovanie iba 3D bufferu:
https://docs.godotengine.org/en/stable/tutorials/3d/resolution_scaling.html

## Nadväzujúca úprava UI a vegetácie

- Kamera má novú čitateľnú ikonu; jej tlačidlo naďalej prepína tri pohľady.
- Starý pás 4×4/DIFF/navijak zostáva skrytý aj pri návrate z menu.
  Ovládanie ostáva v rozbaľovacom menu. Kľúč v strede aj servisná ikona
  používajú vlastný centrovaný obrázok nezávislý od okrajov tlačidla.
- Použité sú používateľom dodané Shapespark stromy (412 trojuholníkov),
  krík a tráva. Calocedrus LOD2 po zjednodušení kmeňa má 3 618 trojuholníkov
  a objavuje sa iba zriedkavo. Každý pripravený model má jeden povrch;
  všetky zdieľajú atlas pôvodných textúr s mipmapami a orezaním priehľadnosti.
- Počet pokusov o dekorácie v lesnom úseku klesol zo 156 na 72, pri hranici
  stromov z 24 na najviac 8. Stromy sa umiestňujú do buniek s rozmerom 12 m
  s malou odchýlkou, aby sa koruny neprekrývali ani cez hranicu úsekov.
  Toto je zníženie hustoty a práce, nie tvrdenie o zmeranom prírastku FPS.
- Zachované sú kolízie kmeňov, kotvy navijaka a pevné hranice mapy.
  Pripravené modely sa dajú otvoriť ako natívne `.tscn` scény v
  `assets/models/vegetation/`; zdrojové FBX a ich veľké textúry sa neexportujú.
- Prešlo 70 kontrol HUD-u a 36 kontrol vegetácie. Skúšky pokrývajú kliknutie
  na kameru, návrat z menu, viacdotykové ovládanie, rozostupy korún,
  deterministické generovanie, textúry a kolízie. Samostatná skúška navijaka
  úspešne pripojila lano k skutočne vygenerovanému stromu, stožiaru aj skale.
- Finálny náhľad bol vizuálne overený v desktopovom aj dotykovom rozložení.
  Všeobecné záseky pri jazde uvedené vyššie tým ešte nie sú uzavreté.
- Všetkých päť záverečných sád prešlo: vegetácia, HUD, usadenie/voda,
  prístupy k budovám (11 385 kontrol) a publikovanie úsekov mapy. Navyše
  prešla samostatná skúška kotiev navijaka. Náhľady v editore a ARM64 APK
  sú obnovené; obsah APK neobsahuje žiadny súbor z importovaných zdrojových
  balíkov rastlín. Reálne zariadenie ani nový prírastok FPS sa tým netestovali.
