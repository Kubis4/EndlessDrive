# Ručné úpravy v Godote

Projekt otvor cez `project.godot` v tomto priečinku:

`C:\Users\KubicaVl\AndroidStudioProjects\EndlessDrive\godot`

Ak už máš Godot otvorený, potvrď **Reload / znovunačítanie zmenených scén**.
Ak sa nezobrazí výzva, zatvor kartu `main` a otvor ju znova zo súborového stromu.
Prípadné vlastné neuložené úpravy si najprv ulož zvlášť.

## 3D scéna, kamera a auto

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
