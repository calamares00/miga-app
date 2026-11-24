#!/usr/bin/env python3
"""Double-meaning icon proposals for Miga, in two styles: rounded line on a pale tile, and white on a colour squircle."""
import json
from pathlib import Path

HAT = "M30 50 C19 50 17 31 31 31 C32 19 50 15 55 25 C62 16 81 21 77 35 C86 39 82 52 70 50 Z"

# (name, idea, svg elements drawn with stroke="currentColor"; fills use currentColor too)
CONCEPTS = [
    ("Chef sonriente", "El gorro y la sonrisa de quien cocina a gusto",
     f'<path d="{HAT}"/><path d="M30 50 L30 76 C30 86 70 86 70 76 L70 50"/><path d="M30 60 L70 60"/><path d="M42 70 C45 77 55 77 58 70"/>'),
    ("Chef m", "Bajo el gorro, la m de Miga",
     f'<path d="{HAT}"/><path d="M30 86 L30 62 C30 51 50 51 50 62 L50 86 M50 62 C50 51 70 51 70 62 L70 86"/>'),
    ("Gorro de pan", "El gorro de chef es una hogaza con sus cortes",
     f'<path d="{HAT}"/><path d="M39 35 L43 41 M52 29 L55 35 M65 33 L68 39"/><path d="M32 50 L32 78 C32 84 68 84 68 78 L68 50"/><path d="M32 62 L68 62"/>'),
    ("Bol m", "Lo que asoma del bol dibuja la m",
     '<path d="M18 54 L82 54 C82 72 68 84 50 84 C32 84 18 72 18 54 Z"/><path d="M28 54 L28 44 C28 32 44 32 44 44 L44 54 M44 44 C44 32 60 32 60 44 L60 54"/><circle cx="70" cy="40" r="3.5" class="f"/><circle cx="76" cy="30" r="2.5" class="f"/>'),
    ("Rebanada feliz", "Una rebanada de pan con cara: sus migas son los ojos",
     '<path d="M27 46 C21 30 34 18 50 18 C66 18 79 30 73 46 C71 51 69 53 69 57 L69 78 C69 83 66 86 61 86 L39 86 C34 86 31 83 31 78 L31 57 C31 53 29 51 27 46 Z"/><circle cx="42" cy="52" r="3.5" class="f"/><circle cx="58" cy="52" r="3.5" class="f"/><path d="M42 66 C45 72 55 72 58 66"/>'),
    ("Libro bol", "Un recetario abierto que también es un bol humeante",
     '<path d="M16 50 C28 46 40 47 50 54 C60 47 72 46 84 50 C84 70 70 84 50 84 C30 84 16 70 16 50 Z"/><path d="M50 54 L50 84"/><path d="M38 38 C34 32 42 28 38 20 M62 38 C58 32 66 28 62 20"/>'),
    ("Olla sonriente", "La tapa es la ceja, el asa la sonrisa",
     '<path d="M24 46 L76 46 L74 76 C74 82 70 86 64 86 L36 86 C30 86 26 82 26 76 Z"/><path d="M20 40 L80 40"/><path d="M44 40 L44 34 L56 34 L56 40"/><path d="M14 54 L24 54 M76 54 L86 54"/><circle cx="41" cy="60" r="3" class="f"/><circle cx="59" cy="60" r="3" class="f"/><path d="M42 70 C45 76 55 76 58 70"/>'),
    ("Tenedor m", "Gira el tenedor y sus púas son una m",
     '<path d="M30 20 L30 40 C30 52 50 52 50 40 L50 20 M50 40 C50 52 70 52 70 40 L70 20"/><path d="M50 48 L50 86"/>'),
    ("Corazón de miga", "Un corazón hecho de miga de pan",
     '<path d="M50 82 C22 62 16 46 22 34 C28 22 44 22 50 34 C56 22 72 22 78 34 C84 46 78 62 50 82 Z"/><circle cx="40" cy="42" r="3" class="f"/><circle cx="56" cy="50" r="3.5" class="f"/><circle cx="46" cy="58" r="2.5" class="f"/><circle cx="62" cy="38" r="2" class="f"/>'),
    ("Miga que habla", "Rebanada y bocadillo de chat: las migas son los puntos de «escribiendo…» del asistente",
     '<path d="M27 44 C21 28 34 16 50 16 C66 16 79 28 73 44 C71 49 69 51 69 55 L69 72 C69 77 66 80 61 80 L40 80 L28 90 L31 78 L31 55 C31 51 29 49 27 44 Z"/><circle cx="40" cy="54" r="3.6" class="f"/><circle cx="50" cy="54" r="3.6" class="f"/><circle cx="60" cy="54" r="3.6" class="f"/>'),
    ("Cuchara i", "La cuchara es la i de «mi» y su miga el punto",
     '<path d="M24 86 L24 60 C24 50 38 50 38 60 L38 86 M38 60 C38 50 52 50 52 60 L52 86"/><ellipse cx="70" cy="44" rx="8" ry="11"/><path d="M70 55 L70 86"/><circle cx="70" cy="22" r="4.5" class="f"/>'),
    ("Gorro corazón", "La copa del gorro de chef dibuja un corazón",
     '<path d="M50 62 C28 50 18 42 21 31 C24 19 41 18 50 30 C59 18 76 19 79 31 C82 42 72 50 50 62 Z"/><path d="M34 54 L34 78 C34 86 66 86 66 78 L66 54"/><path d="M34 70 L66 70"/>'),
]

# Colour sets: line style (tile, stroke) and solid style (squircle top, bottom).
LINE_COLOURS = [("#EEF5F3", "#E8731C"), ("#FFF4E8", "#C1633D"), ("#F1F6EE", "#2F7A57")]
SOLID_COLOURS = [("#FFA24C", "#EE7A1E"), ("#E58A5C", "#B5532F"), ("#5DAE7E", "#2F7A57")]


def tile(svg: str, style: str, colour: int, size: int, uid: str) -> str:
    if style == "line":
        bg, ink = LINE_COLOURS[colour]
        back = f'<rect x="0" y="0" width="120" height="120" rx="30" fill="{bg}"/>'
    else:
        top, bottom = SOLID_COLOURS[colour]
        ink = "#FFFFFF"
        back = (f'<defs><linearGradient id="b{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/>'
                f'<stop offset="1" stop-color="{bottom}"/></linearGradient></defs>'
                f'<rect x="0" y="0" width="120" height="120" rx="30" fill="url(#b{uid})"/>')
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">{back}'
            f'<g transform="translate(14 14) scale(.92)" fill="none" stroke="{ink}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round" style="color:{ink}">'
            f'{svg.replace(chr(39) + "f" + chr(39), "").replace(chr(34) + "f" + chr(34), chr(34) + "f" + chr(34))}</g></svg>')


def main() -> None:
    out = Path("/tmp/logos4")
    css = ".f{fill:currentColor;stroke:none}"
    rows = []
    for i, (name, idea, svg) in enumerate(CONCEPTS):
        line = tile(svg, "line", i % 3, 170, f"l{i}")
        solid = tile(svg, "solid", (i + 1) % 3, 170, f"s{i}")
        rows.append(f'<figure><div class="pair">{line}{solid}</div><figcaption><b>{i + 1}. {name}</b><span>{idea}</span></figcaption></figure>')
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{css}
@font-face {{ font-family: Fraunces; src: url('file://{Path.home()}/lab/calamares00/.tools/fonts/Fraunces.ttf'); font-weight: 100 900; }}
@font-face {{ font-family: Inter; src: url('file://{Path.home()}/lab/calamares00/.tools/fonts/Inter.ttf'); font-weight: 100 900; }}
body {{ margin: 0; padding: 52px 60px; background: #23252B; font-family: Inter, sans-serif; width: 1320px; color: #fff; }}
h1 {{ font: 700 42px Fraunces, serif; margin: 0 0 6px; }} p.sub {{ margin: 0 0 34px; color: #B9BDC6; font-size: 19px; }}
.grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 34px 36px; }}
.pair {{ display: flex; gap: 14px; justify-content: center; }}
figure {{ margin: 0; }} figcaption {{ margin-top: 12px; text-align: center; }}
figcaption b {{ display: block; font-size: 19px; }} figcaption span {{ display: block; font-size: 14.5px; color: #B9BDC6; margin-top: 4px; line-height: 1.35; }}
</style></head><body><h1>Miga · iconos con doble sentido</h1><p class="sub">Cada idea en dos estilos: línea redondeada sobre fondo claro y blanco sobre color.</p>
<div class="grid">{''.join(rows)}</div></body></html>"""
    (out / "icons.html").write_text(html, encoding="utf-8")
    singles = []
    for i, (name, _, svg) in enumerate(CONCEPTS):
        for style, colour in (("line", i % 3), ("solid", (i + 1) % 3)):
            page = f"single_{i + 1}_{style}.html"
            (out / page).write_text(f'<!doctype html><html><head><style>{css} body{{margin:0;background:transparent}}</style></head><body>{tile(svg, style, colour, 512, "x")}</body></html>', encoding="utf-8")
            singles.append([page, f"{i + 1:02d}_{name.lower().replace(' ', '-')}_{'linea' if style == 'line' else 'color'}.png"])
    (out / "singles.json").write_text(json.dumps(singles))


main()
