"""Original vector dashboard symbols and scalable, nine-slice control skins."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "godot/assets/ui"
source = (ROOT / "godot/scripts/item_icons.gd").read_text(encoding="utf-8")
symbols = dict(re.findall(r'"([^"\n]+)": \'([^\n]+)\',', source))
symbols.update({
    "battery": '<path d="M12 20h40v30H12zM19 20v-6h8v6m10 0v-6h8v6M18 32h10m-5-5v10m13-5h10"/>',
    "oil": '<path d="M10 28h25l7 11-13 12H12L7 35h-3m13-7v-8h13v8M35 29l15-10 6 6-14 14M17 20h15"/><path d="M52 38c-7 9-7 15 0 15s7-6 0-15z" fill="#f4f2e8" stroke="none"/>',
    "fuel": '<path d="M13 54V12h23v42M9 54h31M18 18h13v13H18zM36 21h6l9 10v15c0 7-10 7-10 0V34h-5M45 24v9h6"/>',
    "coolant": '<path d="M27 34V11c0-7 10-7 10 0v23a10 10 0 1 1-10 0M32 17v23M8 51q6-6 12 0t12 0 12 0 12 0M8 58q6-6 12 0t12 0 12 0 12 0"/>',
    "brake": '<circle cx="32" cy="32" r="17"/><path d="M11 14a29 29 0 0 0 0 36m42-36a29 29 0 0 1 0 36M32 22v13"/><circle cx="32" cy="42" r="1.8" fill="#f4f2e8" stroke="none"/>',
    "handbrake": '<circle cx="32" cy="32" r="17"/><path d="M11 14a29 29 0 0 0 0 36m42-36a29 29 0 0 1 0 36M27 44V20h8a7 7 0 0 1 0 14h-8"/>',
    "headlights": '<path d="M34 12v40c27-2 27-38 0-40zM8 23l17-7M8 33l17-7M8 43l17-7M8 53l17-7"/>',
    "headlights_high": '<path d="M34 12v40c27-2 27-38 0-40zM7 16h18M7 27h18M7 38h18M7 49h18"/>',
    "drivetrain": '<rect x="10" y="9" width="8" height="13" rx="2"/><rect x="46" y="9" width="8" height="13" rx="2"/><rect x="10" y="42" width="8" height="13" rx="2"/><rect x="46" y="42" width="8" height="13" rx="2"/><path d="M18 16h28M18 48h28M32 16v11m0 10v11"/><rect x="27" y="27" width="10" height="10" rx="2"/>',
    "differential": '<path d="M11 10v14m42-14v14M11 17h42M32 17v10M27 35v-5a5 5 0 0 1 10 0v5"/><rect x="22" y="35" width="20" height="18" rx="3"/><path d="M32 42v5"/>',
    "winch": '<path d="M10 17v28m44-28v28M10 21h44M10 41h44M19 22v18m7-18v18m7-18v18m7-18v18m6-18v18M31 41v7c0 8 12 8 12 0v-3"/>',
    "camera": '<path d="M9 22h11l5-7h14l5 7h11v29H9z"/><circle cx="32" cy="35" r="9"/><path d="M46 28h3"/>',
    "cruise": '<path d="M10 47a24 24 0 1 1 44 0M32 10v7M15 19l5 5m29-5-5 5M9 35h7m32 0h7M32 35l12-12M17 52h30"/><circle cx="32" cy="35" r="3"/>',
    "power": '<path d="M32 9v23M20 14a22 22 0 1 0 24 0"/>',
    "engine": '<path d="M12 25h10l5-8h17v9h9v7h5v13h-5v6H23L14 43H8V28h4M31 17V9m-7 0h14M8 33H3v10h5"/>',
    "tools": '<path d="M40 9a13 13 0 0 0-16 17L9 42a7 7 0 0 0 10 10l16-16A13 13 0 0 0 52 20l-10 9-8-8z"/>',
    "pause": '<path d="M24 17v30m16-30v30" stroke-width="6"/>',
    "chevron_left": '<path d="M39 15L22 32l17 17" stroke-width="5"/>',
    "chevron_right": '<path d="M25 15l17 17-17 17" stroke-width="5"/>',
    "input_mode": '<path d="M9 15h46v34H9zM25 43V25c0-5 8-5 8 0v10l7 2v11M20 35l5 8M20 54h25"/>',
})
symbols["thermometer"] = symbols["coolant"]
symbols["temperature"] = symbols["coolant"]
symbols["brakes"] = symbols["brake"]
symbols["batterycell"] = symbols["battery"]
for name, shape in symbols.items():
    shape = re.sub(r' stroke="#[0-9a-fA-F]+"', '', shape)
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><g fill="none" stroke="#f4f2e8" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round">{shape}</g></svg>\n'
    (UI / f"{name}.svg").write_text(svg, encoding="utf-8")

skins = UI / "skins"
skins.mkdir(exist_ok=True)
palettes = {
    "idle": ("#263947", "#0b141d", "#56778e"),
    "hover": ("#354c5b", "#142330", "#83a2b8"),
    "active": ("#70552b", "#292416", "#edbc69"),
    "primary": ("#836335", "#493819", "#efbf72"),
    "disabled": ("#1c2932", "#101922", "#354a59"),
    "danger": ("#3c2025", "#1c1015", "#d05a60"),
}
for name, (top, bottom, border) in palettes.items():
    for variant in ["", "_left", "_right", "_hub"]:
        key = name + variant
        if variant == "_hub":
            w = h = 128
            shape = '<circle cx="64" cy="64" r="60"'
        else:
            w, h = 256, 128
            if variant == "_left":
                shape = '<path d="M254 3H65a61 61 0 0 0 0 122h189z"'
            elif variant == "_right":
                shape = '<path d="M2 3h189a61 61 0 0 1 0 122H2z"'
            else:
                shape = '<rect x="3" y="3" width="250" height="122" rx="17"'
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs><linearGradient id="surface" x1="0" y1="0" x2="0.2" y2="1"><stop stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient></defs>{shape} fill="url(#surface)" stroke="{border}" stroke-width="2.5"/></svg>\n'
        (skins / f"{key}.svg").write_text(svg, encoding="utf-8")
print(f"Updated {len(symbols)} dashboard symbols and {len(palettes) * 4} vector skins")
