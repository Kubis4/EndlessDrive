# Jazyky

Hra podporuje slovenčinu a angličtinu. Jazyk sa mení v Nastaveniach a ukladá sa do používateľských nastavení, oddelene od rozjazdenej cesty.

Texty v natívnych scénach ostávajú v slovenčine ako čitateľné zdrojové kľúče. Anglický katalóg je `en.json`; `scripts/localization.gd` ho registruje v Godot TranslationServer. Katalóg zahŕňa menu, ovládanie, vozidlo, nálezy a stavové správy. `tr("šablóna %d") % počet` prekladá dynamické šablóny pred dosadením hodnôt. `Locale.translate(správa)` obsluhuje už formátované správy z herných modelov pri zobrazení.

Pri pridávaní textu pridaj zodpovedajúci kľúč do katalógu. Zachovaj zástupné znaky `%s`, `%d`, `%.1f` aj ich poradie; `%%` znamená percento. Nový jazyk dostane vlastný katalóg, položku `LOCALES` a voľbu v menu. Cesty k súborom, identifikátory dielov a údaje uložených hier sa neprekladajú. Export obsahuje `locales/*.json`.

Overenie: `tests/ui_localization_checks.gd` testuje prepínanie, formátované čísla a diely, navijak, natívne ovládanie a viacriadkové texty z Windows scén.
