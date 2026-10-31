#!/usr/bin/env python3
"""Build the pack's simple pixel icons and model files. Requires Pillow."""
import colorsys
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
IDS = json.loads((ROOT / 'tools/items.json').read_text())

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def icon(name):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    hue = int(hashlib.sha256(name.encode()).hexdigest()[:4], 16) / 65535
    color = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, .48, .75))
    dark = tuple(round(c * .48) for c in color)
    light = tuple(min(255, c + 60) for c in color)
    def rect(box, fill): draw.rectangle(box, fill=fill)
    def leaf(x, y, c):
        draw.polygon([(x,y+5),(x+1,y+1),(x+4,y),(x+5,y+3),(x+2,y+6)], fill=c)
        draw.line((x,y+5,x+4,y+1), fill=light)
    if name.startswith('seed_') or name == 'cannabis_plant':
        for x,y in [(3,4),(9,3),(6,9)]:
            rect((x,y,x+2,y+3),dark); rect((x,y,x+1,y+1),color)
        if name == 'cannabis_plant':
            draw.line((8,9,8,2), fill='#6da83b'); leaf(3,1,'#427a31'); leaf(8,0,'#619a39')
    elif name in ('joint','cigarette','blunt','cigar'):
        paper = '#eee7d1' if name in ('joint','cigarette') else '#94613b'
        draw.polygon([(2,11),(11,2),(14,5),(5,14)], fill='#453629')
        draw.polygon([(3,11),(11,3),(13,5),(5,13)], fill=paper)
        draw.line((3,11,5,13), fill='#dc7945', width=2)
        if name in ('cigar','blunt'): draw.line((8,6,10,8),fill='#d7b26c',width=2)
    elif name in ('vape','cart'):
        rect((6,3,10,14), '#233b46'); rect((7,5,9,12), '#4db0bf' if name == 'vape' else '#c19a4d')
        rect((7,1,9,3),'#8eacae'); rect((7,13,9,13),'#b8e6db')
    elif name in ('cannabis_extract','vape_liquid','cbd','glue','energy_drink','bong'):
        fluid = {'cannabis_extract':'#d9ac36','vape_liquid':'#d8833e','cbd':'#72ae4c','glue':'#f1eee0','energy_drink':'#3dd0ce','bong':'#509ba9'}[name]
        rect((6,2,9,5),'#799b9f'); rect((4,6,11,13),'#57818b'); rect((5,7,10,12),fluid)
        rect((6,1,9,2),'#c6dddc'); rect((5,6,5,10),'#d1efed')
        if name == 'bong':
            rect((7,3,8,9),'#9bcdd1'); draw.line((10,10,13,6), fill='#9bcdd1',width=2)
        if name == 'energy_drink':
            draw.line([(8,7),(6,10),(9,10),(7,12)], fill='#f9ef89')
    elif name in ('molly','suboxone','fent','lsd'):
        if name == 'lsd':
            rect((3,3,12,12),'#ece2ca'); rect((4,4,7,7),'#bb68cc'); rect((8,8,11,11),'#62b7b1')
            rect((8,4,11,7),'#82b8d6'); rect((4,8,7,11),'#e3966e')
        else:
            draw.ellipse((3,5,12,11),fill=dark); draw.ellipse((3,4,12,10),fill='#d7e5e6' if name=='fent' else color)
            draw.line((7,5,7,9),fill=light)
    elif name in ('shrooms','harvest_mushroom'):
        rect((7,8,9,13),'#d9c9a4'); draw.polygon([(2,8),(4,4),(8,2),(12,4),(14,8)],fill='#885cab')
        rect((5,5,6,6),'#e9d4ad'); rect((10,6,11,7),'#e9d4ad')
    elif name == 'edible' or name == 'infused_dough':
        draw.ellipse((2,4,13,13),fill='#8c5b32'); draw.ellipse((2,3,13,11),fill='#bf9253' if name=='edible' else '#dec18b')
        for x,y in [(5,5),(9,4),(10,8),(6,9)]: rect((x,y,x+1,y+1),'#446e35')
    elif name in ('heroin','chewing_tobacco','harvest_resin'):
        draw.polygon([(2,11),(4,5),(8,3),(12,6),(13,12),(7,14)],fill=dark)
        draw.polygon([(4,10),(5,5),(8,4),(11,7),(10,11)],fill='#634531' if name!='harvest_resin' else '#d8a339')
    elif name in ('meth','cocaine','nicotine','caffeine'):
        c = '#83dbe9' if name=='meth' else '#ecebd8'
        for x,y in [(2,10),(6,6),(10,9)]:
            draw.polygon([(x,y+3),(x+1,y-3),(x+3,y-4),(x+4,y+2)],fill=c)
            draw.line((x+1,y+1,x+2,y-3),fill='#ffffff')
        if name=='nicotine': rect((5,12,11,13),'#dbc268')
        if name=='caffeine': rect((5,12,11,13),'#b9d6df')
    elif name == 'harvest_coffee':
        for x,y in [(3,4),(9,5),(5,10)]:
            draw.ellipse((x,y,x+3,y+4),fill='#7e4a2e'); draw.line((x+2,y+1,x+1,y+3),fill='#c68a50')
    else:
        # Harvests and loose leaves use botanical silhouettes with distinct palettes.
        green = '#477d38' if name not in ('tobacco','harvest_tobacco') else '#a38846'
        if name.startswith('harvest_'): green = color
        draw.line((5,14,10,3),fill='#877044')
        leaf(2,5,green); leaf(7,2,green); leaf(7,8,green)
    return image

for item in IDS:
    base = ROOT / 'assets/lumidrugs'
    texture = base / f'textures/item/{item}.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    # Retain the original artwork for the three established items.
    legacy = {'joint':'light_15','cart':'command_block_minecart','bong':'structure_void'}.get(item)
    if legacy:
        with Image.open(ROOT / f'assets/minecraft/textures/item/{legacy}.png') as image:
            image.save(texture)
    else:
        icon(item).save(texture)
    write_json(base / f'items/{item}.json', {'model':{'type':'minecraft:model','model':f'lumidrugs:item/{item}'}})
    write_json(base / f'models/item/{item}.json', {'parent':'minecraft:item/generated','textures':{'layer0':f'lumidrugs:item/{item}'}})

write_json(ROOT / 'pack.mcmeta', {'pack': {'min_format':69,'max_format':69,'description':'LumiDrugs — crops, ingredients, drugs and cures (1.21.9–1.21.10)'}})
# Keep source artwork/tools outside the distributable pack.
archive = ROOT.parent / 'LumiDrugs-resourcepack.zip'
with ZipFile(archive, 'w', ZIP_DEFLATED) as out:
    out.write(ROOT / 'pack.mcmeta', 'pack.mcmeta')
    for path in sorted((ROOT / 'assets').rglob('*')):
        if path.is_file(): out.write(path, path.relative_to(ROOT))
print(f'Built {len(IDS)} item models and textures: {archive.name}')
