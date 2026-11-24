#!/usr/bin/env python3
"""The smiling chef icon in Miga's eight colour themes, line and solid styles."""
import json
from pathlib import Path

FONTS = Path.home() / "lab/calamares00/.tools/fonts"

# Miga's colour themes (ui/theme/Color.kt): name, accent, soft accent.
THEMES = [
    ("Huerta", "#2F7A57", "#D3EBDD"),
    ("Terracota", "#C1633D", "#F5DFD4"),
    ("Azul", "#3B6EA5", "#CFE0EF"),
    ("Verde", "#4C8C5B", "#D6EAD9"),
    ("Morado", "#7C5AA0", "#E3D7ED"),
    ("Rosa", "#C25B82", "#F1D7E1"),
    ("Naranja", "#D08A3E", "#F2E0C7"),
    ("Turquesa", "#3E8F94", "#CFE7E8"),
]

HAT = "M30 50 C19 50 17 31 31 31 C32 19 50 15 55 25 C62 16 81 21 77 35 C86 39 82 52 70 50 Z"
CHEF = (f'<path d="{HAT}"/><path d="M30 50 L30 76 C30 86 70 86 70 76 L70 50"/>'
        '<path d="M30 60 L70 60"/><path d="M42 70 C45 77 55 77 58 70"/>')


def lighter(hex_colour: str, amount: float) -> str:
    r, g, b = (int(hex_colour[i:i + 2], 16) for i in (1, 3, 5))
    mix = lambda c: round(c + (255 - c) * amount)
    return f"#{mix(r):02X}{mix(g):02X}{mix(b):02X}"


def darker(hex_colour: str, amount: float) -> str:
    r, g, b = (int(hex_colour[i:i + 2], 16) for i in (1, 3, 5))
    return f"#{round(r * (1 - amount)):02X}{round(g * (1 - amount)):02X}{round(b * (1 - amount)):02X}"


def tile(accent: str, soft: str, solid: bool, size: int, uid: str) -> str:
    if solid:
        back = (f'<defs><linearGradient id="b{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{lighter(accent, .18)}"/>'
                f'<stop offset="1" stop-color="{darker(accent, .12)}"/></linearGradient></defs>'
                f'<rect width="120" height="120" rx="30" fill="url(#b{uid})"/>')
        ink = "#FFFFFF"
    else:
        back = f'<rect width="120" height="120" rx="30" fill="{lighter(soft, .35)}"/>'
        ink = accent
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">{back}'
            f'<g transform="translate(14 13) scale(.92)" fill="none" stroke="{ink}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round">{CHEF}</g></svg>')


def main() -> None:
    out = Path("/tmp/logos7")
    font_css = (f"@font-face {{ font-family: Fraunces; src: url('file://{FONTS}/Fraunces.ttf'); font-weight: 100 900; }}"
                f"@font-face {{ font-family: Inter; src: url('file://{FONTS}/Inter.ttf'); font-weight: 100 900; }}")
    cards = "".join(
        f'<figure><div class="pair">{tile(a, s, False, 150, f"l{i}")}{tile(a, s, True, 150, f"s{i}")}</div><figcaption>{name}</figcaption></figure>'
        for i, (name, a, s) in enumerate(THEMES)
    )
    (out / "icons.html").write_text(f"""<!doctype html><html><head><meta charset="utf-8"><style>{font_css}
body {{ margin: 0; padding: 52px 60px; background: #23252B; font-family: Inter, sans-serif; width: 1320px; color: #fff; }}
h1 {{ font: 650 42px Fraunces, serif; margin: 0 0 6px; }} p.sub {{ margin: 0 0 34px; color: #B9BDC6; font-size: 19px; }}
.grid {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 34px 28px; }}
.pair {{ display: flex; gap: 12px; justify-content: center; }} figure {{ margin: 0; }}
figcaption {{ margin-top: 10px; text-align: center; font-size: 19px; font-weight: 600; }}
</style></head><body><h1>Miga · chef en los colores de la app</h1><p class="sub">Los ocho temas de color de Miga, en línea sobre el tono suave y en blanco sobre el color.</p>
<div class="grid">{cards}</div></body></html>""", encoding="utf-8")
    singles = []
    for i, (name, a, s) in enumerate(THEMES):
        for solid in (False, True):
            page = f"single_{i}_{int(solid)}.html"
            (out / page).write_text(f'<!doctype html><html><head><style>body{{margin:0;background:transparent}}</style></head><body>{tile(a, s, solid, 512, "x")}</body></html>', encoding="utf-8")
            singles.append([page, f"{i + 1}_{name.lower()}_{'color' if solid else 'linea'}.png"])
    (out / "singles.json").write_text(json.dumps(singles))


main()
