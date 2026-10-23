# Endless Drive — 3D implementácia

Stav: 22. 10. 2026. Android debug aj release používajú jediný OpenGL ES renderer.

## Hotové zmeny

- Dve samostatné šípky vľavo; plyn a brzda/cúvanie vpravo. Každý dotyk má vlastné ID. Obe šípky naraz dávajú neutrálny vstup. Pustenie jedného tlačidla neruší ostatné. Pauza, servis a lifecycle vyčistia vstupy.
- Jazda v X/Z, smer, cúvanie, priľnavosť podľa pneumatík/povrchu, náklon a kontakt štyroch kolies s terénom. Pozdĺžna simulácia motora, odpruženia, opotrebenia a hmotnosti ostáva základom.
- Spoločné `World3D` a `WorldProps`: rovnaké deterministické stromy a kamene pre obraz aj kolízie. Kmeň je valec; auto má otočený obdĺžnik. Budovy a vraky používajú test oddelených osí. Pohyb sa priebežne kontroluje proti preletu cez prekážky a zohľadňuje výšku auta.
- Priestorové ramená križovatiek s povrchmi a smerovníkmi. Fyzické zaradenie do ľavého/pravého ramena vyberie vetvu, pokiaľ hráč vopred nezvolil trasu v paneli. Zvolený segment používa pôvodný plán, seed, loot, povrchy a riziko. Ramená sa počas prvých 100 m novej trasy napoja na jej os.
- Prostredie sa mení podľa regiónu: terén, farby, vegetácia a povrch. Prechod biómov je priestorový; farby sa miešajú a vegetácia sa rozptyľuje medzi oboma biómami na úseku 120 m.
- Štyri nové rastrové materiály: piesok, zemina, asfalt a opotrebovaný kov. Atlas `app/src/main/assets/expedition/materials-v1.png` sa pri načítaní rozdelí na štyri 512px opakovateľné GPU textúry s vlastnými mipmapami. Tým sa zabráni miešaniu susedných materiálov na hranách.
- Modulárne auto: správne orientované normály, lak jednotlivých dielov, sklo, chróm, svetlomety, brzdové svetlá, špina a sneh. Predné kolesá zatáčajú, obe nápravy sa odvaľujú. Defekt sploští spodok pneumatiky; pri roztrhnutí zmizne guma a ráfik zostane v plnej veľkosti.
- Objemové ihličnany/listnaté/suché stromy, skaly, kríky, domy so strechou, garáže, dielne, lakovne, čerpacie stanice a relé stožiare.
- Viditeľný náklad expedičného nosiča, reťaze a úžitkový príves. Stav výbavy sa číta z existujúcej hry a save-u.
- Dážď, sneh, hmla, prenasledujúce búrky, prach a stopy, výfukový dym, misfire, para, únik a častice pri poškodení. Zvuk a blesky/misfire zdieľajú časovanie `SceneEffects`.
- Panel CAR používa ten istý 3D model a namontované diely ako jazda. Inventárové kartičky používajú malé UI ikony a farebné vzorky namiesto starých bočných spritov.

## Výkon

Model auta sa načíta a odošle do GPU raz. Snímka mení matice dielov, lak a stav materiálu; CPU už netransformuje približne 20-tisíc vrcholov auta pri každom vykreslení. `CarGeometry` zlučuje materiálové skupiny podľa fyzického dielu.

Terén vzorkuje každú položku výškovej mriežky raz a normály zdieľa medzi trojuholníkmi. Základná mriežka potrebuje 4 070 vzoriek namiesto opakovaného výpočtu výšok/normál pre každý vrchol. Obnova sveta má 32 m kroky s hysteréziou, takže pohyb tesne cez hranicu bunky neprestavuje svet sem a tam. Zmena trasy, regiónu, lootu alebo relé zostáva dôvodom obnovy.

Stromy majú jednoduchšiu vzdialenú geometriu, drobné kríky sa v diaľke nekreslia a deskriptory prekážok majú ohraničenú cache. Stopy si ukladajú výšku pri vzniku. Sneh používa priehľadné štvoruholníky namiesto množstva malých 3D gúľ. Dielňa nevytvára terén ani efekty. Render buffer je obmedzený na 1920 × 1080.

Natívny test zaznamenáva CPU čas snímky a prenesené bajty geometrie. Nameraný údaj z emulátora je diagnostika konkrétnej scény, nie garancia FPS na telefóne.

## Zachované herné systémy a ukladanie

Inventár a kufor, opravy/montáž, lakovanie, kvapaliny a ich kvalita, batéria, palivo, pneumatiky/reťaze, expedičný nosič/príves, počasie/udalosti, budovy/loot, servis, relé, misie, míľniky, rekordy a profil používajú pôvodnú hernú logiku. Nemení sa formát účtu ani vzdialenosť trasy: skóre a postup zostávajú založené na X.

Formát v9 uchováva Z a smer auta. Staré savy od v4 sa dajú načítať. Po obnove sa vozidlo bezpečne zastaví ako predtým. Poloha prejdenej odbočky a zvolené rameno sa uchovávajú aj pre rekonštrukciu terénu; dve staré kompatibilné polia v kodeku sa používajú bez obnovenia starého 2D projekčného kódu.

## Vyradený 2D kód

Zo zdrojov aplikácie sú odstránené `GameRenderer`, `CarArtist`, `GameAssets`/`SedanLayers`, `Camera2D`, `DepthProjection`, parallax/backdrop kreslenie, sprite maliari budov/vegetácie a súvisiace efekty/projekčné konštanty. Nepoužívaných 134 súborov rastrového 2D sveta bolo vyradených z Android resources. HUD a ikony ostávajú bežným Compose UI nad 3D scénou.

Presné pôvodné súbory vrátane necommitnutých úprav a vyradené testy kresieb sú obnoviteľne zachované v ignorovanom `build/legacy-2d-20261022/`. Tento priečinok nie je súčasťou APK. Zmiešané testy boli prispôsobené tak, aby sa zachovali testy hernej logiky.

## Overovanie

- `Driving3DTest`: riadenie, cúvanie, trakcia, stabilita, save.
- `World3DIntegrationTest`: kmene a natočená obálka auta, vertikálne oddelenie, rýchly náraz, deterministické prekážky po save/restore, obe fyzické odbočky a výška po obnove, nemenné GPU meshe, hysterézia sveta, poškodené pneumatiky a 3D dielňa.
- `ExpeditionSceneTest`: OBJ, chýbajúce diely, rebase, nemennosť uloženého stavu a buffery.
- Pôvodné testy motora, kvapalín, hmotnosti, generovania, udalostí, inventára, misií a save-u.
- `ExpeditionViewTest`: skutočné GLES2 na izolovanom Android 15 emulátore, GL error, multitouch šípok/plynu/brzdy, pause/resume/exit, púšť/les/sneh a náhľad CAR. Produkuje kontrolné obrázky v `build/3d-*.png`.

## Aktuálne hranice

Jazda používa arkádové odpruženie a riadenie; nejde o plný rigid-body simulátor ani voľnú sieť ciest vo všetkých smeroch. Alternatívna trasa určí ďalší segment nekonečnej jazdy. Príves má model, hmotnosť a úložisko, jeho 3D zobrazenie zatiaľ používa jednoduché vlečenie za autom bez samostatného kĺbového fyzikálneho telesa.

Tiene sú aproximované geometriou; nie sú zavedené shadow maps. Kríky, ploty a vodiče ostávajú dekoráciou. Textúry a modely sú mobilný štylizovaný základ, ktorý možno ďalej umelecky doladiť.

Záverečné overenie: celá JVM sada 321 testov bez chyby; debug APK a release Kotlin kompilácia prešli. Natívne testy kontrolujú aj obsah textúry a jeho viditeľnosť v paneli. Renderer cieli na 60 Hz počas jazdy, 12 Hz pri státí a 5 Hz pod pauzou alebo servisným panelom.
