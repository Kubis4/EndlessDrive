# Nove pozadia EndlessDrive

Sest novych scen: luky (`rural`), jesenne udolie (`autumn`), skalnaty lom
(`quarry`), mociare (`marsh`), zasnezene hory (`winter_alpine`) a zimny
smrekovy les (`winter_pines`). Stylisticke predlohy su povodne herne
`bg_forest_far.png`, `bg_forest_mid.png`, `bg_forest_near.png`.

## Hotove subory

`tiles/bg_<scena>_<vrstva>.png`: 18 samostatnych RGBA PNG, 2048 x 512 px.

- `far`: nepriehladna obloha a vzdialena krajina.
- `mid`: stredna krajina, priehladnost nad siluetou.
- `near`: blizke stromy, skaly a vegetacia, priehladnost nad siluetou.

Vsetky vrstvy sa opakuju vodorovne. Lava a prava krajna kolona maju
presne zhodne RGBA hodnoty. Horny okraj vrstiev mid/near je priehladny.
Vrstvy su ukotvene spodnou hranou. Sady su zapojene ako stabilne varianty
existujucich biomov podla seedu useku: rural / RURAL, autumn / FOREST_ALIVE,
quarry / WASTELAND, marsh / FOREST a obe zimne sady / ALPINE. Herné pravidla
a format ulozenych hier sa tym nemenia.

## Nahlady a kontrola

`previews/all_backgrounds.jpg` zobrazuje vsetky sceny. Samostatne
`previews/<scena>.jpg` zobrazuju zlozene vrstvy a `<scena>_repeat.jpg`
dve opakovania vedla seba. Nahlady nie su screenshoty z beziacej hry.
`validation.json` obsahuje kontrolu rozmerov, priehladnosti a okrajov
pre kazdy hotovy subor.

## Povod a reprodukcia

Pouzity bol vstavany nastroj image_gen. Presne zadania kazdeho obrazka
su v `manifest.json`, generovane predlohy v `sources/`. Pouzivatel
schvalil nasledne spracovanie skriptom: odstranenie pomocnej farby,
skutocny alfa kanal, zjednotenie rozmerov a spojenie okrajov.

Obnovenie exportov: `python art/backgrounds/prepare_backgrounds.py`
(vyzaduje Pillow a NumPy). PNG podklady su zachovane bez prepisovania
povodnych hernych obrazkov. Pri zapojeni do `TiledArtwork.finish`
pouzite `preserveSeam = true`, aby sa spoje znova neprelinali.
