#!/usr/bin/env python3
"""High-impact icon proposals for Miga: vivid gradients, light and glow, something being revealed."""
import json
from pathlib import Path

FONTS = Path.home() / "lab/calamares00/.tools/fonts"

COMMON_DEFS = """
<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="6"/></filter>
<filter id="drop" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#000" flood-opacity=".35"/></filter>
<linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE7A3"/><stop offset=".5" stop-color="#F5B53F"/><stop offset="1" stop-color="#C77A12"/></linearGradient>
<linearGradient id="paper" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFDF7"/><stop offset="1" stop-color="#EAD9BD"/></linearGradient>
<linearGradient id="crust" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F0A15C"/><stop offset="1" stop-color="#A9501F"/></linearGradient>
<linearGradient id="fire" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#FF3D2E"/><stop offset=".55" stop-color="#FF9A1F"/><stop offset="1" stop-color="#FFE27A"/></linearGradient>
<radialGradient id="light" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFF4C8" stop-opacity="1"/><stop offset=".45" stop-color="#FFD46A" stop-opacity=".55"/><stop offset="1" stop-color="#FFD46A" stop-opacity="0"/></radialGradient>
"""

# (name, what it tells, background stops, svg in a 120x120 tile)
ICONS = [
    ("La cúpula", "Levantas la tapa y algo brilla: ¿qué habrá hoy?", ("#3B1F7A", "#120A33"),
     '<ellipse cx="60" cy="78" rx="46" ry="30" fill="url(#light)" filter="url(#soft)"/>'
     '<rect x="20" y="84" width="80" height="7" rx="3.5" fill="url(#gold)" filter="url(#drop)"/>'
     '<g transform="rotate(-24 34 66)" filter="url(#drop)"><path d="M26 66 C26 44 44 32 62 32 C80 32 98 44 98 66 Z" fill="url(#gold)"/>'
     '<circle cx="62" cy="28" r="5" fill="url(#gold)"/><path d="M40 52 C44 44 52 40 60 39" stroke="#FFF6D6" stroke-width="3" fill="none" stroke-linecap="round" opacity=".8"/></g>'
     '<path d="M78 40 L80 47 L87 49 L80 51 L78 58 L76 51 L69 49 L76 47Z M92 24 L93 28 L97 29 L93 30 L92 34 L91 30 L87 29 L91 28Z" fill="#FFF1B8" filter="url(#glow)"/>'),
    ("Libro de luz", "Abres el recetario y sale el aroma", ("#0F7C78", "#06313A"),
     '<circle cx="60" cy="56" r="40" fill="url(#light)" filter="url(#soft)"/>'
     '<g filter="url(#drop)"><path d="M18 62 C32 56 46 57 58 64 L58 98 C46 91 32 90 18 96 Z" fill="url(#paper)"/>'
     '<path d="M102 62 C88 56 74 57 62 64 L62 98 C74 91 88 90 102 96 Z" fill="url(#paper)"/></g>'
     '<path d="M48 54 C42 44 54 38 48 24 M60 50 C54 38 66 32 60 14 M72 54 C66 44 78 38 72 24" stroke="#FFF3C9" stroke-width="4" fill="none" stroke-linecap="round" filter="url(#glow)"/>'),
    ("Play de pan", "Un vídeo de cocina que se convierte en receta", ("#FF4F7B", "#FF8A3D"),
     '<path d="M40 26 C40 20 46 17 51 20 L92 52 C97 56 97 64 92 68 L51 100 C46 103 40 100 40 94 Z" fill="url(#crust)" filter="url(#drop)" transform="translate(-4 -2)"/>'
     '<path d="M47 36 L82 60 L47 84 Z" fill="#FFF1DC" stroke="#FFF1DC" stroke-width="6" stroke-linejoin="round" transform="translate(-4 -2)"/>'
     '<circle cx="55" cy="55" r="2.6" fill="#E9C9A0"/><circle cx="62" cy="64" r="2" fill="#E9C9A0"/><circle cx="52" cy="70" r="1.8" fill="#E9C9A0"/>'),
    ("Llama m", "El fuego de la cocina con la forma de la m de Miga", ("#1C1A24", "#05050A"),
     '<ellipse cx="60" cy="96" rx="34" ry="8" fill="#FF7A1F" opacity=".35" filter="url(#soft)"/>'
     '<path d="M22 96 C18 74 30 64 32 46 C40 56 40 64 44 70 C46 58 52 48 60 38 C68 48 74 58 76 70 C80 64 80 56 88 46 C90 64 102 74 98 96 Z" fill="url(#fire)" filter="url(#glow)"/>'
     '<path d="M40 96 L40 82 C40 74 52 74 52 82 L52 96 M52 82 C52 74 64 74 64 82 L64 96 M64 82 C64 74 76 74 76 82 L76 96" stroke="#1C1A24" stroke-width="6.5" fill="none" stroke-linecap="round" opacity=".9"/>'),
    ("Chapuzón", "La cuchara se lanza al bol: ¡a probar!", ("#5B5BF0", "#2A1E8F"),
     '<path d="M20 70 L100 70 C100 92 82 104 60 104 C38 104 20 92 20 70 Z" fill="url(#paper)" filter="url(#drop)"/>'
     '<ellipse cx="60" cy="70" rx="40" ry="7" fill="#F5B53F"/>'
     '<g transform="rotate(32 66 40)" filter="url(#drop)"><ellipse cx="66" cy="58" rx="9" ry="12" fill="#E6ECFF"/><rect x="63" y="14" width="6" height="40" rx="3" fill="#E6ECFF"/></g>'
     '<circle cx="40" cy="56" r="4" fill="#FFD27A"/><circle cx="32" cy="46" r="2.6" fill="#FFD27A"/><circle cx="84" cy="54" r="3.2" fill="#FFD27A"/><circle cx="90" cy="44" r="2" fill="#FFD27A"/>'),
    ("Huevo al sol", "Un huevo frito que es un sol saliendo: buenos días, ¿qué cocinamos?", ("#FFB347", "#FF5E3A"),
     '<path d="M18 76 C14 58 32 48 44 54 C52 40 78 40 86 54 C102 54 110 74 98 88 C88 100 66 98 54 96 C36 100 20 92 18 76 Z" fill="#FFFDF7" filter="url(#drop)"/>'
     '<circle cx="62" cy="72" r="17" fill="url(#gold)" filter="url(#glow)"/>'
     '<path d="M62 22 L62 34 M32 34 L40 43 M92 34 L84 43" stroke="#FFF6D6" stroke-width="4.5" stroke-linecap="round"/>'),
    ("Pila de recetarios", "Tus libros de recetas, uno encima de otro, con su marcapáginas", ("#FFD86B", "#F29E2E"),
     '<g filter="url(#drop)"><rect x="22" y="80" width="76" height="16" rx="4" fill="#2F7A57"/><rect x="26" y="84" width="68" height="3" fill="#E7F2EA" opacity=".6"/>'
     '<rect x="28" y="62" width="68" height="16" rx="4" fill="#C1633D" transform="rotate(-4 62 70)"/>'
     '<rect x="24" y="44" width="70" height="16" rx="4" fill="#3E6FD8" transform="rotate(3 59 52)"/></g>'
     '<path d="M78 44 L78 30 L84 34 L90 30 L90 46" fill="#E8542F" transform="rotate(3 84 38)"/>'
     '<path d="M50 38 C46 32 54 28 50 20 M60 36 C56 30 64 26 60 16" stroke="#FFFFFF" stroke-width="3.4" fill="none" stroke-linecap="round" opacity=".85"/>'),
    ("Órbita", "Todo gira alrededor de tu cocina: libros, vídeos, compra y favoritos", ("#11243F", "#050B18"),
     '<circle cx="60" cy="60" r="30" fill="url(#light)" opacity=".7" filter="url(#soft)"/>'
     '<ellipse cx="60" cy="60" rx="46" ry="18" fill="none" stroke="#7FA8FF" stroke-width="1.6" opacity=".6" transform="rotate(-18 60 60)"/>'
     '<path d="M40 56 L80 56 C80 70 72 78 60 78 C48 78 40 70 40 56 Z" fill="url(#paper)" filter="url(#drop)"/>'
     '<path d="M52 48 C49 44 55 41 52 36 M62 48 C59 44 65 41 62 36" stroke="#FFE7A3" stroke-width="2.6" fill="none" stroke-linecap="round"/>'
     '<circle cx="17" cy="72" r="7" fill="#FF6B57" filter="url(#glow)"/><path d="M14 69 L21 72 L14 75Z" fill="#fff"/>'
     '<circle cx="103" cy="47" r="7" fill="#4CC38A" filter="url(#glow)"/><path d="M100 45 L106 45 L105 50 L101 50Z" fill="#fff"/>'
     '<circle cx="86" cy="80" r="5" fill="#FFC94D" filter="url(#glow)"/><circle cx="32" cy="38" r="5" fill="#C58BFF" filter="url(#glow)"/>'),
    ("Miga mágica", "La rebanada que brilla: el asistente que te ayuda a cocinar", ("#7B2FF7", "#F107A3"),
     '<circle cx="60" cy="62" r="36" fill="url(#light)" filter="url(#soft)" opacity=".8"/>'
     '<path d="M34 54 C28 38 42 26 58 26 C74 26 88 38 82 54 C80 59 78 61 78 65 L78 92 C78 97 75 100 70 100 L46 100 C41 100 38 97 38 92 L38 65 C38 61 36 59 34 54 Z" fill="url(#crust)" filter="url(#drop)"/>'
     '<path d="M42 56 C40 46 48 38 58 38 C68 38 76 46 74 56 C73 60 70 62 70 66 L70 90 L46 90 L46 66 C46 62 43 60 42 56 Z" fill="#FFF6E6"/>'
     '<path d="M90 18 L94 32 L108 36 L94 40 L90 54 L86 40 L72 36 L86 32Z" fill="#FFF7C2" filter="url(#glow)"/>'
     '<path d="M24 30 L26 35 L31 37 L26 39 L24 44 L22 39 L17 37 L22 35Z" fill="#FFF7C2" filter="url(#glow)"/>'),
]


def tile(svg: str, stops: tuple, size: int, uid: str) -> str:
    top, bottom = stops
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
<defs>{COMMON_DEFS}<linearGradient id="bg{uid}" x1="0" y1="0" x2=".6" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>
<radialGradient id="shine{uid}" cx=".28" cy=".12" r=".75"><stop offset="0" stop-color="#fff" stop-opacity=".32"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/></radialGradient>
<clipPath id="c{uid}"><rect width="120" height="120" rx="30"/></clipPath></defs>
<g clip-path="url(#c{uid})"><rect width="120" height="120" fill="url(#bg{uid})"/>{svg}<rect width="120" height="120" fill="url(#shine{uid})"/></g>
<rect x=".5" y=".5" width="119" height="119" rx="29.5" fill="none" stroke="#fff" stroke-opacity=".15"/></svg>"""


def main() -> None:
    out = Path("/tmp/logos6")
    font_css = (f"@font-face {{ font-family: Fraunces; src: url('file://{FONTS}/Fraunces.ttf'); font-weight: 100 900; }}"
                f"@font-face {{ font-family: Inter; src: url('file://{FONTS}/Inter.ttf'); font-weight: 100 900; }}")
    cards = "".join(
        f'<figure>{tile(svg, stops, 250, str(i))}<figcaption><b>{i + 1}. {name}</b><span>{idea}</span></figcaption></figure>'
        for i, (name, idea, stops, svg) in enumerate(ICONS)
    )
    (out / "icons.html").write_text(f"""<!doctype html><html><head><meta charset="utf-8"><style>{font_css}
body {{ margin: 0; padding: 52px 64px; background: #101114; font-family: Inter, sans-serif; width: 1312px; color: #fff; }}
h1 {{ font: 650 44px Fraunces, serif; margin: 0 0 6px; }} p.sub {{ margin: 0 0 36px; color: #A9AEB8; font-size: 19px; }}
.grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 40px; }}
figure {{ margin: 0; display: flex; flex-direction: column; align-items: center; }} svg {{ filter: drop-shadow(0 16px 28px rgba(0,0,0,.45)); }}
figcaption {{ margin-top: 14px; text-align: center; max-width: 330px; }} figcaption b {{ display: block; font-size: 20px; }}
figcaption span {{ display: block; font-size: 15px; color: #A9AEB8; margin-top: 4px; line-height: 1.35; }}
</style></head><body><h1>Miga · iconos con impacto</h1><p class="sub">Color intenso, luz y algo que se revela: cada uno cuenta una parte de la app.</p>
<div class="grid">{cards}</div></body></html>""", encoding="utf-8")
    singles = []
    for i, (name, _, stops, svg) in enumerate(ICONS):
        page = f"single_{i + 1}.html"
        (out / page).write_text(f'<!doctype html><html><head><meta charset="utf-8"><style>{font_css} body{{margin:0;background:transparent}}</style></head><body>{tile(svg, stops, 512, "x")}</body></html>', encoding="utf-8")
        slug = name.lower().replace(" ", "-").replace("ú", "u").replace("á", "a").replace("ó", "o").replace("í", "i")
        singles.append([page, f"{i + 1:02d}_{slug}.png"])
    (out / "singles.json").write_text(json.dumps(singles))


main()
