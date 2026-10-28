# Papierové menu a otvorená cesta

Implementované 27. 10. 2026 podľa výberu používateľa: papierová navigácia z návrhu 3 a auto s cestou z návrhu 1.

- Pozadie: `paper-road.png`, vytvorené vstavaným imagegen, bez textov a tlačidiel. Pôvodný výstup je skopírovaný bez úprav.
- Natívne ovládanie: `scenes/ui/main_menu.tscn`, záložné programové rozloženie v `scripts/menu.gd`, papierový štýl v `scripts/menu_skin.gd`.
- Názov a všetkých šesť akcií zostávajú editovateľné v Godote. Texty používajú existujúcu lokalizáciu. Grafika auta je ilustrácia v pozadí menu, nie zmena modelu počas jazdy.
- Zlatá akcia sa prepína medzi Novou jazdou a Pokračovať podľa dostupnosti uloženej jazdy. Počas prípravy sveta sú akcie zamknuté a na papieri sa zobrazuje priebeh.
- Rozloženie 1440 × 810 sa rovnomerne prispôsobuje obrazovke; pri inom pomere strán zostávajú tmavé okraje bez orezania auta alebo tlačidiel. Podobrazovky používajú existujúce olivové panely.
- `next.svg` je natívna vektorová šípka hlavnej akcie.

## Overenie

Natívne snímky a kontrola rozloženia: `tests/paper_menu_capture.gd` (1280 × 720, 1920 × 1080, 1920 × 900 a 1024 × 768; uložená/nová jazda, loading, klávesnica, angličtina a modálne okná). Výstupy: `build/paper-menu/`.

Funkčné sady: `menu_ui_checks`, `ui_localization_checks`, `editor_scene_checks`. Android APK sa v tejto úprave nevytvára.

## Presné zadanie pre imagegen

Use case: compositing. Production background art for the actual game main menu, landscape 16:9. Combine two attached concept images: image 1 is the expedition paper menu style, image 2 is the road scene with cream vintage IZH delivery van viewed rear three-quarter. Output ONLY BACKGROUND ART with absolutely no letters, numbers, logos, titles, text, buttons, rules, arrows or interface icons anywhere. Composition must precisely support a native UI overlay: left 38% of full image is a nearly upright warm ivory paper sheet from image 1, subtle aged paper grain and worn edges, quiet very light interior, no stains across its interior, fully blank from 5% to 33% image width and 8% to 91% image height. Paper occupies full image height and has an irregular right edge near 38% width, subtle realistic shadow, a slight natural lean at edge but all interior unobstructed. Right 62% is the beautiful open-road landscape from image 2 with the SAME cream compact delivery van seen rear three-quarter on winding worn asphalt, position van centered around 68% width and 70% height, entire van visible, golden hour mountain lake and pine landscape behind. Keep original car shape, camera angle, natural warm olive and gold palette from image 2. A faint paper-map contour pattern confined to the extreme edge where paper meets scene can tie them together; main right scene is full landscape and van, no tiny toy on a map. No compass or props over left paper. Preserve sunrise and depth. High quality crisp art suitable for 1920x1080 full screen. Remove ALL existing title/menu typography from both references. No baked UI whatsoever.
