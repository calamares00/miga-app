#!/usr/bin/env python3
"""Grown-up double-meaning icons for Miga: negative space, fine lines, restrained palettes."""
import json
import math
from pathlib import Path

FONTS = Path.home() / "lab/calamares00/.tools/fonts"

# Palettes: (background top, background bottom, ink)
PALETTES = {
    "bosque": ("#2C5546", "#17332A", "#F3E9D7"),
    "carbon": ("#34302C", "#1B1917", "#D9875A"),
    "burdeos": ("#6B2534", "#3E1520", "#E8C88A"),
    "marino": ("#243552", "#141F36", "#E9D9BC"),
    "crema": ("#F7F1E7", "#EADFCF", "#2B2A28"),
    "oliva": ("#737B4E", "#4C5434", "#F6EFDF"),
    "terracota": ("#C46A43", "#94431F", "#FCEFE2"),
}


def crumbs_m() -> str:
    """An m drawn with crumbs: dots along two arches and three legs, smaller towards the end."""
    points = []
    for x in (22, 50, 78):
        for k in range(4):
            points.append((x, 82 - k * 9))
    for cx in (36, 64):
        for k in range(7):
            a = math.pi - k * math.pi / 6
            points.append((cx + 14 * math.cos(a), 46 - 14 * math.sin(a)))
    dots = "".join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{3.8 - (i % 5) * 0.35:.2f}" fill="var(--ink)"/>' for i, (x, y) in enumerate(points))
    return dots


# (name, idea, palettes, svg in a 100x100 box using var(--ink) and var(--bg))
ICONS = [
    ("Dos hogazas", "Dos panes con sus cortes que, juntos, son la M", ("bosque", "terracota"),
     '<path d="M12 84 L12 52 C12 20 50 20 50 52 C50 20 88 20 88 52 L88 84 Z" fill="var(--ink)"/>'
     '<path d="M21 48 L29 37 M32 45 L40 34 M60 48 L68 37 M71 45 L79 34" stroke="var(--bg)" stroke-width="4" stroke-linecap="round"/>'),
    ("Vapor m", "Del bol sube el vapor y dibuja la m", ("carbon", "crema"),
     '<path d="M16 62 L84 62 C84 78 70 88 50 88 C30 88 16 78 16 62 Z" fill="var(--ink)"/>'
     '<path d="M30 54 L30 34 C30 22 50 22 50 34 L50 54 M50 34 C50 22 70 22 70 34 L70 54" stroke="var(--ink)" stroke-width="3.4" fill="none" stroke-linecap="round"/>'),
    ("Sello de obrador", "Un sello de panadería: el recetario como oficio", ("burdeos", "crema"),
     '<circle cx="50" cy="50" r="40" stroke="var(--ink)" stroke-width="2.2" fill="none"/>'
     '<circle cx="50" cy="50" r="33" stroke="var(--ink)" stroke-width="1.2" fill="none"/>'
     '<defs><path id="arcTop" d="M22 50 A28 28 0 0 1 78 50"/><path id="arcBottom" d="M20 52 A30 30 0 0 0 80 52"/></defs>'
     '<text font-family="Inter" font-weight="700" font-size="8.4" letter-spacing="3.2" fill="var(--ink)"><textPath href="#arcTop" startOffset="50%" text-anchor="middle">MIGA</textPath></text>'
     '<text font-family="Inter" font-weight="600" font-size="5.6" letter-spacing="2.4" fill="var(--ink)"><textPath href="#arcBottom" startOffset="50%" text-anchor="middle">RECETARIO</textPath></text>'
     '<path d="M36 58 C36 46 43 41 50 41 C57 41 64 46 64 58 Z" fill="var(--ink)"/>'
     '<path d="M44 49 L47 45 M50 50 L53 46" stroke="var(--bg)" stroke-width="1.8" stroke-linecap="round"/>'),
    ("Libro y bol", "El recetario abierto es también un bol", ("marino", "oliva"),
     '<path d="M14 46 C27 42 40 43 50 50 C60 43 73 42 86 46 C86 70 70 86 50 86 C30 86 14 70 14 46 Z" stroke="var(--ink)" stroke-width="3" fill="none" stroke-linejoin="round"/>'
     '<path d="M50 50 L50 86" stroke="var(--ink)" stroke-width="3"/>'
     '<path d="M40 36 C37 31 43 28 40 21 M60 36 C57 31 63 28 60 21" stroke="var(--ink)" stroke-width="2.6" fill="none" stroke-linecap="round"/>'),
    ("Espiga M", "Dos espigas de trigo que forman la M", ("crema", "bosque"),
     '<path d="M24 86 L24 30 L50 62 L76 30 L76 86" stroke="var(--ink)" stroke-width="3.4" fill="none" stroke-linejoin="round" stroke-linecap="round"/>'
     + "".join(f'<ellipse cx="{x + s * 6}" cy="{y}" rx="3.2" ry="6.4" transform="rotate({s * 32} {x + s * 6} {y})" fill="var(--ink)"/>'
               for x in (24, 76) for y in (22, 34, 46) for s in (-1, 1))
     + '<ellipse cx="24" cy="16" rx="3" ry="6" fill="var(--ink)"/><ellipse cx="76" cy="16" rx="3" ry="6" fill="var(--ink)"/>'),
    ("Migas", "Una m hecha de migas", ("terracota", "marino"), crumbs_m()),
    ("Rebanada y página", "La rebanada es una página de receta", ("oliva", "carbon"),
     '<path d="M26 44 C20 28 34 16 50 16 C66 16 80 28 74 44 C72 49 70 51 70 55 L70 80 C70 85 67 88 62 88 L38 88 C33 88 30 85 30 80 L30 55 C30 51 28 49 26 44 Z" stroke="var(--ink)" stroke-width="3" fill="none" stroke-linejoin="round"/>'
     '<path d="M40 50 L60 50 M40 60 L60 60 M40 70 L54 70" stroke="var(--ink)" stroke-width="3" stroke-linecap="round"/>'),
    ("M editorial", "La M de una buena revista de cocina, con su miga", ("bosque", "burdeos"),
     '<text x="46" y="78" text-anchor="middle" font-family="Fraunces" font-weight="560" font-size="74" fill="var(--ink)" style="font-variation-settings:\'opsz\' 144, \'SOFT\' 0">M</text>'
     '<circle cx="84" cy="76" r="4" fill="var(--ink)"/>'),
    ("Libro tenedor", "El tenedor es el lomo del recetario abierto", ("carbon", "terracota"),
     '<path d="M16 34 C27 31 38 32 46 37 L46 80 C38 75 27 74 16 77 Z M84 34 C73 31 62 32 54 37 L54 80 C62 75 73 74 84 77 Z" stroke="var(--ink)" stroke-width="3" fill="none" stroke-linejoin="round"/>'
     '<path d="M44 14 L44 26 C44 30 46 32 50 32 C54 32 56 30 56 26 L56 14 M50 14 L50 26 M50 32 L50 90" stroke="var(--ink)" stroke-width="3" fill="none" stroke-linecap="round"/>'),
    ("Cuchara hoja", "La cuchara tiene forma de hoja: cocina de temporada", ("crema", "oliva"),
     '<path d="M50 12 C66 22 70 42 50 58 C30 42 34 22 50 12 Z" fill="var(--ink)"/>'
     '<path d="M50 20 L50 52 M50 32 L57 26 M50 42 L43 36" stroke="var(--bg)" stroke-width="2.2" stroke-linecap="round"/>'
     '<path d="M50 58 L50 90" stroke="var(--ink)" stroke-width="5" stroke-linecap="round"/>'),
    ("Olla m", "La m de Miga, en hueco, en el cuerpo de la olla", ("burdeos", "bosque"),
     '<path d="M20 40 L80 40 L77 80 C76 86 72 88 66 88 L34 88 C28 88 24 86 23 80 Z" fill="var(--ink)"/>'
     '<rect x="14" y="32" width="72" height="7" rx="3.5" fill="var(--ink)"/><rect x="43" y="24" width="14" height="7" rx="3" fill="var(--ink)"/>'
     '<path d="M8 50 L18 50 M82 50 L92 50" stroke="var(--ink)" stroke-width="5" stroke-linecap="round"/>'
     '<path d="M34 78 L34 62 C34 54 50 54 50 62 L50 78 M50 62 C50 54 66 54 66 62 L66 78" stroke="var(--bg)" stroke-width="5" fill="none" stroke-linecap="round"/>'),
    ("Miga y cuchara", "En la rebanada, la cuchara se dibuja con la miga", ("marino", "terracota"),
     '<path d="M26 44 C20 28 34 16 50 16 C66 16 80 28 74 44 C72 49 70 51 70 55 L70 80 C70 85 67 88 62 88 L38 88 C33 88 30 85 30 80 L30 55 C30 51 28 49 26 44 Z" fill="var(--ink)"/>'
     '<ellipse cx="50" cy="42" rx="8" ry="11" fill="var(--bg)"/><rect x="47.5" y="50" width="5" height="28" rx="2.5" fill="var(--bg)"/>'),
]


def tile(svg: str, palette: str, size: int, uid: str) -> str:
    top, bottom, ink = PALETTES[palette]
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg" style="--ink:{ink};--bg:{bottom}">
<defs><linearGradient id="b{uid}" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient></defs>
<rect width="120" height="120" rx="30" fill="url(#b{uid})"/>
<g transform="translate(14 14) scale(.92)">{svg}</g></svg>"""


def main() -> None:
    out = Path("/tmp/logos5")
    font_css = (f"@font-face {{ font-family: Fraunces; src: url('file://{FONTS}/Fraunces.ttf'); font-weight: 100 900; }}"
                f"@font-face {{ font-family: Inter; src: url('file://{FONTS}/Inter.ttf'); font-weight: 100 900; }}")
    cards = "".join(
        f'<figure><div class="pair">{tile(svg, p1, 170, f"a{i}")}{tile(svg, p2, 170, f"b{i}")}</div>'
        f'<figcaption><b>{i + 1}. {name}</b><span>{idea}</span><i>{p1} · {p2}</i></figcaption></figure>'
        for i, (name, idea, (p1, p2), svg) in enumerate(ICONS)
    )
    (out / "icons.html").write_text(f"""<!doctype html><html><head><meta charset="utf-8"><style>{font_css}
body {{ margin: 0; padding: 52px 60px; background: #ECE8E1; font-family: Inter, sans-serif; width: 1320px; color: #1D2520; }}
h1 {{ font: 600 42px Fraunces, serif; margin: 0 0 6px; }} p.sub {{ margin: 0 0 34px; color: #5A645E; font-size: 19px; }}
.grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 36px 36px; }}
.pair {{ display: flex; gap: 14px; justify-content: center; }} svg {{ filter: drop-shadow(0 10px 18px rgba(0,0,0,.18)); }}
figure {{ margin: 0; }} figcaption {{ margin-top: 12px; text-align: center; }}
figcaption b {{ display: block; font-size: 19px; }} figcaption span {{ display: block; font-size: 14.5px; color: #5A645E; margin-top: 4px; }}
figcaption i {{ display: block; font-size: 13px; color: #8A938C; margin-top: 2px; font-style: normal; }}
</style></head><body><h1>Miga · línea adulta</h1><p class="sub">Doble sentido con formas limpias, espacio en negativo y línea fina. Cada idea en dos paletas.</p>
<div class="grid">{cards}</div></body></html>""", encoding="utf-8")
    singles = []
    for i, (name, _, (p1, p2), svg) in enumerate(ICONS):
        for palette in (p1, p2):
            page = f"single_{i + 1}_{palette}.html"
            (out / page).write_text(f'<!doctype html><html><head><meta charset="utf-8"><style>{font_css} body{{margin:0;background:transparent}}</style></head><body>{tile(svg, palette, 512, "x")}</body></html>', encoding="utf-8")
            slug = name.lower().replace(" ", "-").replace("á", "a").replace("í", "i").replace("ó", "o")
            singles.append([page, f"{i + 1:02d}_{slug}_{palette}.png"])
    (out / "singles.json").write_text(json.dumps(singles))


main()
