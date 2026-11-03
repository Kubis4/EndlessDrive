"""The game's icon as SVG layers: the road winding up to the summit with the transmitter, the
truck on it, under an evening sky. Run it, then tools/render_icon.gd turns the layers into
the PNGs of assets/icon. Canvas 432 px; Android keeps the middle 264 px circle safe in every
mask, so what matters stands there and only the land and the sky run out to the edges.
    python tools/make_icon.py <folder for the SVGs>
"""
import math
import os
import sys

SIZE = 432


# The road's bends: height on the canvas, middle, half width.
ROAD = [(460, 186, 104), (432, 190, 84), (350, 206, 44), (306, 268, 24), (272, 236, 14), (252, 170, 8), (234, 196, 4), (222, 232, 1.5)]


def road_polygon():
    """The S of the road, wide at the bottom and narrowing to nothing at the mountain's foot."""
    left, right = [], []
    points = [ROAD[0]] + ROAD + [ROAD[-1]]
    for k in range(1, len(points) - 2):
        for i in range(16):
            t = i / 16
            value = []
            for c in range(3):
                # Catmull-Rom through the bends.
                a, b, d, e = points[k - 1][c], points[k][c], points[k + 1][c], points[k + 2][c]
                value.append(0.5 * (2 * b + (d - a) * t + (2 * a - 5 * b + 4 * d - e) * t * t + (3 * b - a - 3 * d + e) * t * t * t))
            left.append((value[1] - value[2], value[0]))
            right.append((value[1] + value[2], value[0]))
    left.append((ROAD[-1][1] - ROAD[-1][2], ROAD[-1][0]))
    right.append((ROAD[-1][1] + ROAD[-1][2], ROAD[-1][0]))
    return " ".join("%.1f,%.1f" % p for p in left + right[::-1])


def defs():
    return """<defs>
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#0f2740"/><stop offset="0.3" stop-color="#28617f"/>
<stop offset="0.48" stop-color="#d98a58"/><stop offset="0.62" stop-color="#ffd27e"/>
</linearGradient>
<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
<stop offset="0" stop-color="#fff3c4" stop-opacity="0.95"/><stop offset="0.35" stop-color="#ffdf96" stop-opacity="0.55"/>
<stop offset="1" stop-color="#ffcf80" stop-opacity="0"/>
</radialGradient>
<linearGradient id="land" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#2f7350"/><stop offset="1" stop-color="#143526"/>
</linearGradient>
<linearGradient id="road" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#f0d7a2"/><stop offset="1" stop-color="#b98a55"/>
</linearGradient>
<linearGradient id="body" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#7d8a52"/><stop offset="1" stop-color="#4d5833"/>
</linearGradient>
</defs>"""


def background():
    return """<rect width="432" height="432" fill="url(#sky)"/>
<circle cx="296" cy="188" r="104" fill="url(#glow)"/>
<circle cx="296" cy="188" r="30" fill="#fff4cb"/>
<path d="M-10 118C60 100 120 112 176 104S300 92 442 116V128C330 112 260 122 180 120S50 124-10 134Z" fill="#f3b27c" opacity="0.38"/>
<path d="M-10 66C80 52 150 66 240 56S380 50 442 62V70C360 62 300 72 230 72S70 70-10 80Z" fill="#7fb0bf" opacity="0.28"/>
<path d="M-10 262L44 206L92 232L130 196L176 240H262L312 200L352 230L392 192L442 240V300H-10Z" fill="#46596e"/>"""


def foreground():
    road = road_polygon()
    return """<path d="M216 132L110 252H322Z" fill="#535a70"/>
<path d="M216 132L322 252H226L206 210L224 182L208 158Z" fill="#373c52"/>
<path d="M216 132L172 183L190 174L200 194L214 174L228 196L240 178L261 184Z" fill="#f6f9fb"/>
<path d="M216 132L261 184L240 178L228 196L222 181L228 165L214 150Z" fill="#c9d6e4"/>
<rect x="213" y="92" width="6" height="46" fill="#20242f"/>
<path d="M216 100L229 136H224L216 112L208 136H203Z" fill="#20242f"/>
<rect x="204" y="118" width="24" height="3.5" fill="#20242f"/>
<circle cx="216" cy="90" r="12" fill="#ff5a3c" opacity="0.35"/>
<circle cx="216" cy="90" r="6" fill="#ff4b33"/>
<path d="M-10 236C50 216 104 230 160 222S290 212 340 224S410 228 442 216V442H-10Z" fill="url(#land)"/>
<polygon points="%s" fill="url(#road)"/>
<g transform="translate(196,346) scale(0.66) translate(-216,-358)"><ellipse cx="216" cy="358" rx="82" ry="11" fill="#0d1a14" opacity="0.5"/>
<rect x="160" y="326" width="24" height="33" rx="6" fill="#14130f"/>
<rect x="248" y="326" width="24" height="33" rx="6" fill="#14130f"/>
<path d="M170 252H262L274 288L280 298V336H152V298L158 288Z" fill="url(#body)"/>
<path d="M170 252H262L267 268H165Z" fill="#94a269"/>
<path d="M174 266H258L267 296H165Z" fill="#a8d6e4"/>
<path d="M174 266H206L186 296H165Z" fill="#d7eff5"/>
<rect x="152" y="298" width="128" height="7" fill="#3a4227"/>
<rect x="158" y="308" width="22" height="16" rx="3" fill="#ff4032"/>
<rect x="252" y="308" width="22" height="16" rx="3" fill="#ff4032"/>
<rect x="194" y="312" width="44" height="12" rx="2" fill="#efe7cf"/>
<rect x="146" y="332" width="140" height="13" rx="4" fill="#1d1c17"/></g>""" % road


def monochrome():
    """One colour for the themed icon: the summit with its mast, and the truck under it."""
    return """<path fill="#fff" d="M213 92H219V126L252 172L232 164L218 184L204 164L180 172L213 126Z"/>
<path fill="#fff" d="M173 183L197 174L205 190L218 196L232 188L240 180L259 186L300 244H132Z"/>
<path fill="#fff" transform="translate(216,348) scale(0.76) translate(-216,-356)" fill-rule="evenodd" d="M170 256H262L274 288L280 298V342H270V356H248V342H184V356H162V342H152V298L158 288ZM176 268L167 296H265L256 268ZM158 306V322H180V306ZM252 306V322H274V306ZM194 312V324H238V312Z"/>"""


def svg(content):
    return '<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 432 432">%s\n%s\n</svg>\n' % (defs(), content)


def whole():
    return ('<clipPath id="round"><rect width="432" height="432" rx="88"/></clipPath><g clip-path="url(#round)">%s\n%s</g>' % (background(), foreground()))


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for name, content in [("icon", whole()), ("adaptive_background", background()), ("adaptive_foreground", foreground()), ("adaptive_monochrome", monochrome())]:
    with open(os.path.join(out, name + ".svg"), "w", encoding="utf-8") as file:
        file.write(svg(content))
print("written to", out)
