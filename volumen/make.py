#!/usr/bin/env python3
"""Icon proposals for Miga: rich (gradients, depth, grain) Android-style squircles. Writes icons.html."""
import json
from pathlib import Path

# Background themes: (top-left, bottom-right, glyph light, glyph shade, accent)
THEMES = {
    "terracota": ("#E58A5C", "#A6472A", "#FFF7EE", "#F3D6BF", "#FFD27A"),
    "huerta": ("#7FB77E", "#2F6B4A", "#FBFFF5", "#D8EBCF", "#F6D36B"),
    "teal": ("#3EC6B8", "#0E6F73", "#F4FFFD", "#CBEFEA", "#FFC86B"),
    "atardecer": ("#FFB25B", "#E0496D", "#FFF8F0", "#FFE0CC", "#FFF1A8"),
    "noche": ("#2B3A4A", "#0F1620", "#FFE9C7", "#E8B98A", "#FF9A5C"),
    "crema": ("#FFF6E8", "#F1DCC0", "#C1633D", "#8C3F22", "#7FB77E"),
    "uva": ("#9B7BE0", "#4A2F8F", "#FFF6FF", "#E6D9FF", "#FFC2E2"),
    "limon": ("#FFE16B", "#F2A33A", "#FFFFFF", "#FFF0C8", "#5FA05C"),
}

# Glyphs drawn in a 100x100 box; G = main fill (gradient), S = shade, A = accent, B = background-ish cut.
GLYPHS = {
    "pan": """
      <path d="M18 62 C18 40 32 28 50 28 C68 28 82 40 82 62 C82 70 78 74 70 74 L30 74 C22 74 18 70 18 62Z" fill="url(#g)"/>
      <path d="M34 40 L42 52 M48 36 L56 50 M62 40 L68 52" stroke="var(--s)" stroke-width="5" stroke-linecap="round"/>
      <ellipse cx="50" cy="74" rx="32" ry="5" fill="var(--s)" opacity=".55"/>""",
    "rebanada": """
      <path d="M26 44 C22 30 34 20 50 20 C66 20 78 30 74 44 C72 50 70 52 70 56 L70 80 C70 84 67 86 63 86 L37 86 C33 86 30 84 30 80 L30 56 C30 52 28 50 26 44Z" fill="url(#crust)"/>
      <path d="M34 46 C32 36 40 30 50 30 C60 30 68 36 66 46 C65 50 62 52 62 56 L62 78 L38 78 L38 56 C38 52 35 50 34 46Z" fill="#FFF6E6"/>
      <circle cx="46" cy="50" r="3" fill="#E9CFA9"/><circle cx="56" cy="60" r="2.4" fill="#E9CFA9"/><circle cx="45" cy="66" r="2" fill="#E9CFA9"/><circle cx="55" cy="44" r="1.8" fill="#E9CFA9"/>""",
    "libro": """
      <path d="M14 30 C26 26 40 27 49 34 L49 80 C40 73 26 72 14 76Z" fill="url(#g)"/>
      <path d="M86 30 C74 26 60 27 51 34 L51 80 C60 73 74 72 86 76Z" fill="url(#g)"/>
      <path d="M49 34 L49 80 M51 34 L51 80" stroke="var(--s)" stroke-width="2.5"/>
      <path d="M22 40 C30 38 37 39 42 42 M22 50 C30 48 37 49 42 52 M58 42 C63 39 70 38 78 40 M58 52 C63 49 70 48 78 50" stroke="var(--s)" stroke-width="3" stroke-linecap="round" fill="none"/>
      <path d="M62 18 C58 24 66 26 62 32" stroke="var(--a)" stroke-width="3.5" stroke-linecap="round" fill="none"/>""",
    "bol": """
      <path d="M16 50 L84 50 C84 70 70 82 50 82 C30 82 16 70 16 50Z" fill="url(#g)"/>
      <rect x="38" y="80" width="24" height="6" rx="3" fill="var(--s)"/>
      <path d="M38 40 C34 34 42 30 38 22 M52 40 C48 32 56 28 52 18 M66 40 C62 34 70 30 66 22" stroke="url(#g)" stroke-width="4" stroke-linecap="round" fill="none" opacity=".9"/>
      <path d="M70 56 C76 50 86 50 88 44 C82 42 72 44 70 56Z" fill="var(--a)"/>""",
    "gorro": """
      <path d="M30 56 C18 54 16 36 30 34 C30 22 46 16 54 26 C62 16 80 22 76 36 C88 38 84 56 70 56Z" fill="url(#g)"/>
      <rect x="32" y="54" width="36" height="22" rx="4" fill="url(#g)"/>
      <path d="M32 66 L68 66" stroke="var(--s)" stroke-width="4"/>
      <path d="M64 72 C70 62 82 62 86 56 C78 52 66 58 64 72Z" fill="var(--a)"/>""",
    "olla": """
      <path d="M22 44 L78 44 L76 76 C76 81 72 84 67 84 L33 84 C28 84 24 81 24 76Z" fill="url(#g)"/>
      <rect x="18" y="38" width="64" height="8" rx="4" fill="var(--s)"/>
      <rect x="44" y="31" width="12" height="7" rx="3" fill="var(--s)"/>
      <path d="M14 52 L22 52 M78 52 L86 52" stroke="url(#g)" stroke-width="5" stroke-linecap="round"/>
      <path d="M50 70 C42 64 44 56 50 60 C56 56 58 64 50 70Z" fill="var(--a)"/>
      <path d="M40 24 C37 20 43 18 40 12 M60 24 C57 20 63 18 60 12" stroke="url(#g)" stroke-width="3.5" stroke-linecap="round" fill="none"/>""",
    "m": """
      <text x="50" y="74" text-anchor="middle" font-family="Fraunces" font-weight="800" font-size="72" fill="url(#g)" font-style="italic">m</text>
      <circle cx="80" cy="28" r="5" fill="var(--a)"/><circle cx="72" cy="20" r="3" fill="var(--a)"/><circle cx="86" cy="40" r="2.5" fill="var(--a)"/>""",
    "magia": """
      <path d="M24 46 C20 34 32 24 46 24 C60 24 72 34 68 46 C66 52 64 54 64 58 L64 80 C64 84 61 86 57 86 L35 86 C31 86 28 84 28 80 L28 58 C28 54 26 52 24 46Z" fill="url(#g)"/>
      <circle cx="40" cy="52" r="2.6" fill="var(--s)"/><circle cx="50" cy="62" r="3.2" fill="var(--s)"/><circle cx="40" cy="70" r="2" fill="var(--s)"/><circle cx="54" cy="46" r="2" fill="var(--s)"/>
      <path d="M76 16 L79 26 L89 29 L79 32 L76 42 L73 32 L63 29 L73 26Z" fill="var(--a)"/>
      <path d="M84 48 L85.5 52.5 L90 54 L85.5 55.5 L84 60 L82.5 55.5 L78 54 L82.5 52.5Z" fill="var(--a)"/>""",
    "tabla": """
      <rect x="24" y="30" width="52" height="56" rx="12" fill="url(#g)"/>
      <rect x="44" y="14" width="12" height="20" rx="6" fill="url(#g)"/><circle cx="50" cy="21" r="3" fill="var(--b)"/>
      <path d="M50 74 C36 64 38 50 48 52 C49 52 50 54 50 55 C50 54 51 52 52 52 C62 50 64 64 50 74Z" fill="var(--a)"/>""",
    "cubiertos": """
      <path d="M38 14 L38 34 C38 40 42 42 44 44 L42 86 C42 89 46 89 46 86 L48 44 C50 42 54 40 54 34 L54 14" stroke="url(#g)" stroke-width="0" fill="none"/>
      <rect x="36" y="14" width="4" height="22" rx="2" fill="url(#g)"/><rect x="44" y="14" width="4" height="22" rx="2" fill="url(#g)"/><rect x="52" y="14" width="4" height="22" rx="2" fill="url(#g)"/>
      <path d="M36 34 C36 42 40 44 44 46 L43 84 C43 88 49 88 49 84 L48 46 C52 44 56 42 56 34Z" fill="url(#g)"/>
      <ellipse cx="66" cy="28" rx="9" ry="14" fill="url(#g)"/>
      <path d="M63 40 L62 84 C62 88 68 88 68 84 L67 40Z" fill="url(#g)"/>
      <path d="M68 66 C74 58 84 58 88 52 C80 48 70 54 68 66Z" fill="var(--a)"/>""",
    "cesta": """
      <path d="M16 46 L84 46 L76 82 C75 85 72 86 69 86 L31 86 C28 86 25 85 24 82Z" fill="url(#g)"/>
      <path d="M32 54 L36 78 M50 54 L50 78 M68 54 L64 78" stroke="var(--s)" stroke-width="4" stroke-linecap="round"/>
      <path d="M28 46 C30 30 40 22 50 22 C60 22 70 30 72 46" stroke="url(#g)" stroke-width="5" fill="none"/>
      <rect x="20" y="30" width="38" height="10" rx="5" transform="rotate(-28 39 35)" fill="var(--a)"/>
      <path d="M62 44 C66 34 78 32 82 26 C72 24 62 32 62 44Z" fill="#7FB77E"/>""",
    "espiga": """
      <path d="M50 88 L50 30" stroke="url(#g)" stroke-width="4" stroke-linecap="round"/>
      <g fill="url(#g)">
        <ellipse cx="50" cy="22" rx="5" ry="9"/>
        <ellipse cx="42" cy="36" rx="5" ry="9" transform="rotate(-35 42 36)"/><ellipse cx="58" cy="36" rx="5" ry="9" transform="rotate(35 58 36)"/>
        <ellipse cx="42" cy="50" rx="5" ry="9" transform="rotate(-35 42 50)"/><ellipse cx="58" cy="50" rx="5" ry="9" transform="rotate(35 58 50)"/>
        <ellipse cx="42" cy="64" rx="5" ry="9" transform="rotate(-35 42 64)"/><ellipse cx="58" cy="64" rx="5" ry="9" transform="rotate(35 58 64)"/>
      </g>
      <circle cx="74" cy="26" r="4" fill="var(--a)"/>""",
}

# The proposals: (name, glyph, theme)
ICONS = [
    ("Pan de casa", "pan", "terracota"),
    ("Miga", "rebanada", "crema"),
    ("Recetario", "libro", "huerta"),
    ("Bol caliente", "bol", "atardecer"),
    ("Chef verde", "gorro", "teal"),
    ("Cocina con cariño", "olla", "terracota"),
    ("Monograma m", "m", "noche"),
    ("Miga mágica", "magia", "uva"),
    ("Tabla y corazón", "tabla", "limon"),
    ("A la mesa", "cubiertos", "huerta"),
    ("La compra", "cesta", "teal"),
    ("Espiga", "espiga", "atardecer"),
    ("Miga nocturna", "rebanada", "noche"),
    ("Recetario teal", "libro", "teal"),
    ("Monograma huerta", "m", "huerta"),
    ("Miga mágica cálida", "magia", "terracota"),
]


def icon_svg(glyph: str, theme: str, uid: str, size: int) -> str:
    top, bottom, light, shade, accent = THEMES[theme]
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg"
  style="--s:{shade};--a:{accent};--b:{bottom}">
  <defs>
    <linearGradient id="bg{uid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>
    <radialGradient id="shine{uid}" cx=".3" cy=".15" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".45"/><stop offset=".55" stop-color="#fff" stop-opacity="0"/></radialGradient>
    <linearGradient id="crust" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#D98A4E"/><stop offset="1" stop-color="#9A4B22"/></linearGradient>
    <linearGradient id="g{uid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{light}"/><stop offset="1" stop-color="{shade}"/></linearGradient>
    <filter id="depth{uid}" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="2.2" stdDeviation="2.2" flood-color="#000" flood-opacity=".28"/>
    </filter>
    <filter id="grain{uid}"><feTurbulence type="fractalNoise" baseFrequency="1.6" numOctaves="2" seed="7"/>
      <feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .09 0"/><feComposite in2="SourceGraphic" operator="in"/></filter>
    <clipPath id="clip{uid}"><rect x="4" y="4" width="112" height="112" rx="30"/></clipPath>
  </defs>
  <g clip-path="url(#clip{uid})">
    <rect x="4" y="4" width="112" height="112" fill="url(#bg{uid})"/>
    <circle cx="96" cy="100" r="46" fill="#fff" opacity=".06"/>
    <circle cx="18" cy="16" r="30" fill="#fff" opacity=".07"/>
    <g transform="translate(10 10)" filter="url(#depth{uid})">{GLYPHS[glyph].replace('url(#g)', f'url(#g{uid})')}</g>
    <rect x="4" y="4" width="112" height="112" fill="url(#shine{uid})"/>
    <rect x="4" y="4" width="112" height="112" filter="url(#grain{uid})" fill="#000"/>
  </g>
  <rect x="4.5" y="4.5" width="111" height="111" rx="29.5" fill="none" stroke="#fff" stroke-opacity=".18"/>
</svg>"""


def main() -> None:
    out = Path("/tmp/logos3")
    fonts = Path.home() / "lab/calamares00/.tools/fonts"
    cards = "".join(
        f'<figure>{icon_svg(glyph, theme, str(i), 240)}<figcaption><b>{i + 1}. {name}</b><span>{theme}</span></figcaption></figure>'
        for i, (name, glyph, theme) in enumerate(ICONS)
    )
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fraunces; src: url('file://{fonts}/Fraunces.ttf'); font-weight: 100 900; font-style: normal italic; }}
@font-face {{ font-family: Inter; src: url('file://{fonts}/Inter.ttf'); font-weight: 100 900; }}
body {{ margin: 0; padding: 56px 64px; background: #F4F2EE; font-family: Inter, sans-serif; width: 1312px; }}
h1 {{ font: 700 44px Fraunces, serif; margin: 0 0 8px; color: #1D2520; }} p {{ margin: 0 0 36px; color: #5A645E; font-size: 20px; }}
.grid {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 34px 40px; }}
figure {{ margin: 0; display: flex; flex-direction: column; align-items: center; }}
svg {{ filter: drop-shadow(0 14px 22px rgba(0,0,0,.16)); }}
figcaption {{ margin-top: 12px; text-align: center; }} figcaption b {{ display: block; font-size: 19px; color: #1D2520; }}
figcaption span {{ font-size: 15px; color: #8A938C; }}
</style></head><body><h1>Miga · iconos con volumen</h1><p>Degradados, sombra suave, brillo y grano. Squircle como los iconos adaptativos de Android.</p>
<div class="grid">{cards}</div></body></html>"""
    (out / "icons.html").write_text(html, encoding="utf-8")
    # Each icon alone at 512 for a closer look.
    singles = [[f"single_{i + 1}.html", f"{i + 1:02d}_{glyph}_{theme}.png"] for i, (_, glyph, theme) in enumerate(ICONS)]
    for i, (_, glyph, theme) in enumerate(ICONS):
        (out / f"single_{i + 1}.html").write_text(
            f"""<!doctype html><html><head><meta charset="utf-8"><style>@font-face {{ font-family: Fraunces; src: url('file://{fonts}/Fraunces.ttf'); font-weight: 100 900; font-style: normal italic; }}
body{{margin:0;background:transparent}}</style></head><body>{icon_svg(glyph, theme, 's', 512)}</body></html>""", encoding="utf-8")
    (out / "singles.json").write_text(json.dumps(singles))


main()
