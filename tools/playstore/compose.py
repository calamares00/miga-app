#!/usr/bin/env python3
"""
Google Play phone screenshots: a headline, a short line and the real app (from StoreShotsTest and
FavoritesShotsTest) inside a phone frame, 1080x1920 (9:16) PNG without transparency, as Play
asks for phone screenshots that can be featured.

Usage: compose.py <raw dir with es/ and en/> <output dir>
Needs Pillow (PYTHONPATH with .tools/pylib) and Playwright (node, see .tools/playwright/env.sh).
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

WIDTH, HEIGHT = 1080, 1920
FONTS = Path(os.environ.get("MIGA_FONTS", Path.home() / "lab/calamares00/.tools/fonts"))

# (raw screenshot, theme, headline with *highlighted* words, line) per language, in listing order.
# No third-party brands in the texts (see docs/google-play.md).
SLIDES = {
    "es": [
        ("01_books", "cream", "Tu recetario *familiar*, siempre contigo", "Libros con portada para las recetas de casa"),
        ("03_recipe", "sage", "Recetas *claras*, de un vistazo", "Ingredientes, raciones y tiempos sin buscar"),
        ("16_share_instagram", "terracotta", "Guarda recetas de *reels* y *vídeos*", "Compártelos con Miga y quedan como receta"),
        ("20_fav_all", "cream", "Sigue a tus *cocineros* favoritos", "Canales, perfiles y webs de recetas en un solo sitio"),
        ("05_cook_mode", "night", "Cocina con las *manos libres*", "Pasos en grande, temporizadores y lectura en voz alta"),
        ("06_shopping", "sage", "La compra, ordenada por *pasillos*", "Añade los ingredientes de una receta con un toque"),
        ("10_recipe_health", "cream", "Tus *alérgenos* y raciones, a la vista", "Información orientativa, sin conexión"),
        ("14_ideas", "terracotta", "¿Qué cocino *hoy*?", "Ideas con lo que tienes, con tu propio asistente"),
    ],
    "en": [
        ("01_books", "cream", "Your *family* cookbook, always with you", "Books with covers for your home recipes"),
        ("03_recipe", "sage", "Recipes that are *clear* at a glance", "Ingredients, servings and times without searching"),
        ("16_share_instagram", "terracotta", "Save recipes from *reels* and *videos*", "Share them to Miga and keep them as recipes"),
        ("20_fav_all", "cream", "Follow your favourite *cooks*", "Channels, profiles and recipe websites in one place"),
        ("05_cook_mode", "night", "Cook *hands-free*", "Big steps, timers and reading aloud"),
        ("06_shopping", "sage", "Shopping sorted by *aisle*", "Add a recipe's ingredients with one tap"),
        ("10_recipe_health", "cream", "Your *allergens* and portions, at a glance", "A guide, and it works offline"),
        ("14_ideas", "terracotta", "What shall I cook *today*?", "Ideas from what you have, with your own assistant"),
    ],
}

THEMES = {
    # background, second background colour, headline, line, highlight
    "cream": ("#F7F1E6", "#EFE4D2", "#1D2520", "#5A645E", "#C1633D"),
    "sage": ("#E8EEE4", "#D6E2D0", "#1D2520", "#4E5B51", "#2F7A57"),
    "terracotta": ("#C1633D", "#A9532F", "#FFFFFF", "#FBE6DB", "#FFE2A8"),
    "night": ("#1A1E1B", "#0D100E", "#F3F5F1", "#B9C2BB", "#E08A66"),
}


def data_url(path: Path, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode()


def headline_html(text: str, highlight: str) -> str:
    parts = text.split("*")
    return "".join(f'<em style="color:{highlight}">{p}</em>' if i % 2 else p for i, p in enumerate(parts))


def slide_html(shot: Path, theme: str, headline: str, line: str) -> str:
    bg, bg2, ink, soft, accent = THEMES[theme]
    image = Image.open(shot).convert("RGB")
    status = "#%02x%02x%02x" % image.getpixel((image.width // 2, 2))
    status_ink = "#000" if sum(image.getpixel((image.width // 2, 2))) > 380 else "#fff"
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fraunces; src: url('{data_url(FONTS / "Fraunces.ttf", "font/ttf")}'); font-weight: 100 900; }}
@font-face {{ font-family: Inter; src: url('{data_url(FONTS / "Inter.ttf", "font/ttf")}'); font-weight: 100 900; }}
* {{ margin: 0; box-sizing: border-box; }}
body {{ width: {WIDTH}px; height: {HEIGHT}px; overflow: hidden; position: relative;
  background: radial-gradient(ellipse 120% 70% at 50% 100%, {bg2} 0%, {bg} 62%); font-family: Inter, sans-serif; }}
.blob {{ position: absolute; border-radius: 50%; background: {accent}; opacity: .10; }}
.text {{ position: absolute; top: 118px; left: 84px; right: 84px; text-align: center; }}
h1 {{ font-family: Fraunces, serif; font-weight: 640; font-size: 88px; line-height: 1.06; letter-spacing: -1.5px; color: {ink};
  font-variation-settings: 'SOFT' 50, 'opsz' 144; }}
h1 em {{ font-style: italic; font-weight: 700; padding-right: .06em; }}
p {{ margin-top: 30px; font-size: 40px; line-height: 1.3; color: {soft}; font-weight: 500; }}
.phone {{ position: absolute; left: 50%; transform: translateX(-50%); top: 530px; width: 860px; padding: 24px;
  border-radius: 118px; background: #111412; box-shadow: 0 50px 90px -20px rgba(0,0,0,.38), 0 0 0 3px #2b302d inset; }}
.screen {{ border-radius: 94px; overflow: hidden; background: {status}; position: relative; }}
.status {{ height: 66px; display: flex; align-items: center; justify-content: space-between; padding: 0 58px 0 62px;
  color: {status_ink}; font: 600 30px Inter, sans-serif; }}
.camera {{ position: absolute; top: 20px; left: 50%; width: 30px; height: 30px; margin-left: -15px; border-radius: 50%; background: #0b0d0c; }}
.icons {{ display: flex; gap: 12px; align-items: center; }}
.screen img {{ display: block; width: 100%; }}
</style></head><body>
<div class="blob" style="width:520px;height:520px;left:-200px;top:300px"></div>
<div class="blob" style="width:380px;height:380px;right:-150px;top:40px"></div>
<div class="text"><h1>{headline_html(headline, accent)}</h1><p>{line}</p></div>
<div class="phone"><div class="screen">
  <div class="status"><span>9:41</span><div class="icons">
    <svg width="34" height="24" viewBox="0 0 34 24"><path fill="currentColor" d="M2 20h4v4H2zM10 14h4v10h-4zM18 8h4v16h-4zM26 2h4v22h-4z"/></svg>
    <svg width="30" height="24" viewBox="0 0 24 18"><path fill="currentColor" d="M12 18 0 5a17 17 0 0 1 24 0z"/></svg>
    <svg width="20" height="30" viewBox="0 0 14 24"><rect x="1" y="3" width="12" height="20" rx="2" fill="currentColor"/><rect x="4" y="0" width="6" height="3" rx="1" fill="currentColor"/></svg>
  </div></div>
  <div class="camera"></div>
  <img src="{data_url(shot, 'image/png')}">
</div></div>
</body></html>"""


RENDER = r"""
const { chromium } = require('playwright');
(async () => {
  const jobs = JSON.parse(require('fs').readFileSync(process.argv[2], 'utf8'));
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: %d, height: %d }, deviceScaleFactor: 1 });
  for (const [html, out] of jobs) {
    await page.goto('file://' + html);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(150);
    await page.screenshot({ path: out });
  }
  await browser.close();
})();
""" % (WIDTH, HEIGHT)


def main(raw: Path, out: Path) -> None:
    work = Path(tempfile.mkdtemp(prefix="playshots-"))
    jobs = []
    for lang, slides in SLIDES.items():
        (out / lang).mkdir(parents=True, exist_ok=True)
        for number, (name, theme, headline, line) in enumerate(slides, start=1):
            shot = raw / lang / f"{name}.png"
            html = work / f"{lang}_{number}.html"
            html.write_text(slide_html(shot, theme, headline, line), encoding="utf-8")
            jobs.append([str(html), str(work / f"{lang}_{number}.png")])
    (work / "jobs.json").write_text(json.dumps(jobs))
    script = work / "render.js"
    script.write_text(RENDER)
    subprocess.run(["node", str(script), str(work / "jobs.json")], check=True, cwd=os.environ.get("PLAYWRIGHT_DIR", "."))
    for lang, slides in SLIDES.items():
        for number, (name, *_rest) in enumerate(slides, start=1):
            # Play takes 24-bit PNG: no transparency.
            target = out / lang / f"{number}_{name.split('_', 1)[1]}.png"
            Image.open(work / f"{lang}_{number}.png").convert("RGB").save(target, optimize=True)
            print(target)


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
