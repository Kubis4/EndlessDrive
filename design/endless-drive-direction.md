# Sirocco: smerovanie podľa EndlessDrive

Podklad: lokálny projekt `../EndlessDrive`, najmä `README.md`, `godot/README.md`, `godot/scripts/run_state.gd`, `terrain_profile.gd` a `stop_catalog.gd`. Návrh odlišuje cieľový koncept od súčasnej implementácie v Sirocco.

## Cieľový zážitok

Jedna dlhá cesta sa postupne odhaľuje pred autom. Hráč sleduje prejdenú vzdialenosť, stav auta a zásob; podľa krajiny a počasia rozhoduje, či pokračovať, odbočiť k budove alebo zastaviť a opraviť auto. Cesta sa začína na asfalte, neskôr prechádza cez štrk, hlinité cesty a náročnejšie prostredia. Odbočky ponúkajú rozdielne riziká a zásoby. Depá sú spoľahlivejšie body na doplnenie, nie povinný návrat do štartového tábora.

## Už v Sirocco

- Samostatná generovaná výprava s konečnou mapou, asfal­tovým začiatkom, brodom, odbočkami a obchádzkou.
- Existujúca fyzika vozidla, povrchov, vody, dažďa a nákladu.
- Jednoduché zásoby paliva, oleja, chladiacej kvapaliny a opráv; nájdená väčšia nádrž.
- Zastávky sú zatiaľ jednoduché prototypy; uloženie uchováva stav výpravy.

## Rozdiely, ktoré treba ešte implementovať

1. Nahradiť konečnú mapu postupne načítanými úsekmi cesty a ukladať absolútnu prejdenú vzdialenosť. Tvorba úseku a jeho znovuvytvorenie musia závisieť od seedu a čísla úseku, nie poradia načítania. Vo veľkých vzdialenostiach bude potrebný posúvaný počiatok súradníc.
2. Vytvoriť križovatky s trvalou voľbou vetvy a pri obnovení načítať tú istú trasu. Prístup k zastávkam a alternatívam musí byť fyzicky prejazdný.
3. Zmeniť zastávky na tematické domy, dielne, čerpacie stanice a depá s identitami nezávislými od načítania úsekov; rozmiestnenie paliva musí zaručiť rozumnú vzdialenosť medzi zdrojmi.
4. Nahradiť jednu hodnotu stavu auta stavom motora, pneumatík, bŕzd, pruženia, chladiča a alternátora. Doplniť batériu, teplotu, typy a kvalitu dielov a kvapalín. Nadviazať ich účinky na existujúcu fyziku Sirocco.
5. Ukladať voľby trás, stav zastávok, inventár, stav dielov a vzdialenosť; verzovať formát a testovať hranice úsekov aj obnovu počas jazdy.
6. Pripraviť dlhé jazdné testy s rôznymi seedmi, počasím, dostupnosťou paliva a prejazdom križovatiek. Až potom označiť cestu za plnohodnotnú nekonečnú výpravu.

Asfaltová textúra pochádza z lokálneho `EndlessDrive/godot/assets/textures/weathered-asphalt.png`; tamojší `ROAD_SURFACES.md` uvádza, že ide o samostatnú procedurálne vytvorenú textúru bez ďalších externých licencií.
