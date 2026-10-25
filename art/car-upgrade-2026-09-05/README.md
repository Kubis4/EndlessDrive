# Detailový upgrade auta

Upravených 17 produkčných PNG: základná karoséria, 11 montovaných dielov, štyri typy kolies a reťazový overlay. Po odsúhlasení používateľom boli upravené iba RGB hodnoty existujúcich pixelov programovým postupom; imagegen sa pri aute nepoužil.

`originals/` obsahuje zálohu každého súboru pred touto úpravou. `final/` obsahuje výsledky nasadené pod rovnakými názvami v `app/src/main/res/drawable-nodpi/`. Pre návrat stačí skopírovať PNG z `originals/` naspäť do tohto adresára.

Rozmery PNG, všetky hodnoty alfa kanála, priehľadné výrezy, obrysy a polohy detailov ostali nezmenené. Zachované sú aj pôvodné RGB pixely pri hranách siluety a presná klasifikácia lakovateľných pixelov podľa `SedanLayers.splitPaintLayers` na zdrojovom rozlíšení. Kód, kotvy dielov, polomery kolies a body svetiel neboli upravené.

Vizuálna úprava zvyšuje jemný kontrast existujúcich detailov, odlesky kovu a svetiel, pridáva tlmený odlesk modrého skla a jemné tieňovanie spodnej časti laku. Svetelné pole používa súradnice zloženého auta, aby oddelené lakované diely dostali konzistentnú úpravu. Čalúnenie dostáva veľmi jemnú väzbu textúry; pneumatiky a disky výraznejšie pôvodné drážky a lúče.

[upgrade.py](upgrade.py) reprodukuje výsledky zo zálohy. [validation.json](validation.json) zaznamenáva pôvodné SHA-256, rozmery, hash alfa kanála a nulový počet zmien alfa kanála aj klasifikácie laku pre všetkých 17 dielov. Kontroly overujú aj nezmenený bounding box.

[assembly-review.jpg](assembly-review.jpg) porovnáva zloženie podľa pôvodných kotiev a simuluje modré prelakovanie. [detail-review.jpg](detail-review.jpg) porovnáva dvere a športové koleso. Ide o offline náhľady, nie snímky z bežiacej hry. Android build ani beh na zariadení nebol pri tejto grafickej úprave spustený.
