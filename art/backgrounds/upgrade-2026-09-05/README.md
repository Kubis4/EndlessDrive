# Vizuálny upgrade pozadí

21 PNG vrstiev pre sedem prostredí: priemysel, púšť, súmrak na púšti, piesočná búrka, prachová búrka, suchý les a živý les. Vytvorené vstavaným nástrojom imagegen; zadania a zdrojové cesty sú v [manifest.json](manifest.json).

Finálne obrázky sú v `final/` a nasadené pod pôvodnými názvami v `app/src/main/res/drawable-nodpi/`. Každý má 2048 × 512 pixelov. `far` je nepriehľadná vrstva, `mid` a `near` majú skutočný alfa kanál. Pôvodné súbory pred týmto upgrade sú v `originals/`.

[process.py](process.py) odstráni prípadnú namaľovanú šachovnicu, opraví prázdny priestor pod terénom, obnoví približnú výšku siluet podľa predlôh a dorovná rozmery. Pri dvoch slnečných oblohách upraví vertikálne rozloženie prázdnej oblohy, aby slnko zostalo kruhové. Horizontálny spoj vytvorí krátkym prelínaním v premultiplikovanej RGBA; pri škálovaní spracúva premultiplikované kanály, aby okolo priehľadnosti nevznikli tmavé lemy. Zdrojové generované PNG sú v lokálnej imagegen cache uvedenej v manifeste.

Kontrola všetkých 21 exportov overuje rozmery, nepriehľadnosť vzdialenej vrstvy, priehľadný horný priestor ostatných vrstiev, plný spodný okraj a úplnú zhodu RGBA ľavého a pravého okraja. Výsledky sú vo [validation.json](validation.json). [tile-review.jpg](tile-review.jpg) ukazuje každú zloženú sadu dvakrát vedľa seba a bol vizuálne skontrolovaný. Je to náhľad vrstiev, nie snímka z bežiacej hry; beh na Android zariadení nebol v tejto úprave overený.

Kód hry ostáva pre tieto PNG kompatibilný. Existujúci `TiledArtwork` naďalej pridáva svoju textúru a upravuje dlaždice pri načítaní, preto sa vzhľad v hre môže mierne líšiť od priameho náhľadu.
