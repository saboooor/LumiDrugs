#!/usr/bin/env python3
"""Build the pack's textured pixel icons and model files. Requires Pillow."""
from collections import defaultdict
import colorsys
import hashlib
import json
import shutil
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image, ImageDraw
from flavor_icons import flavor_icon
from surface_detail import add_material_detail

ROOT = Path(__file__).resolve().parents[1]
IDS = json.loads((ROOT / 'tools/items.json').read_text())
FLAVORS = json.loads((ROOT / 'tools/flavors.json').read_text())
# Append only: the original 50 custom model data IDs must stay stable.
VARIANTS = {f'{product}_flavor_{flavor}': (product, flavor, color)
            for product in FLAVORS['products']
            for flavor, color in FLAVORS['flavors'].items()}
EXTRA_ITEMS = json.loads((ROOT / 'tools/extra-items.json').read_text())
IDS += list(VARIANTS) + list(EXTRA_ITEMS)

MATERIALS = {
    'heroin': 'dried_kelp',
    'fent': 'iron_nugget',
    'shrooms': 'brown_mushroom',
    'meth': 'quartz',
    'blunt': 'stick',
    'cocaine': 'sugar',
    'joint': 'paper',
    'cart': 'blaze_rod',
    'edible': 'cookie',
    'glue': 'milk_bucket',
    'molly': 'popped_chorus_fruit',
    'lsd': 'purple_dye',
    'salvia': 'fern',
    'tobacco': 'dried_kelp',
    'nicotine': 'sugar',
    'cigarette': 'paper',
    'cigar': 'stick',
    'chewing_tobacco': 'brown_dye',
    'vape': 'blaze_rod',
    'caffeine': 'glowstone_dust',
    'energy_drink': 'honey_bottle',
    'suboxone': 'blue_egg',
    'cbd': 'large_fern',
    'bong': 'glass_bottle',
    'infused_dough': 'wheat',
    'cannabis_extract': 'potion',
    'vape_liquid': 'potion',
    'cannabis_plant': 'fern',
    'seed_cannabis': 'fern',
    'seed_poppy': 'wheat_seeds',
    'seed_mushroom': 'wheat_seeds',
    'seed_ephedra': 'wheat_seeds',
    'seed_coca': 'wheat_seeds',
    'seed_sassafras': 'wheat_seeds',
    'seed_ergot': 'wheat_seeds',
    'seed_salvia': 'wheat_seeds',
    'seed_resin': 'wheat_seeds',
    'seed_tobacco': 'wheat_seeds',
    'seed_coffee': 'wheat_seeds',
    'harvest_cannabis': 'fern',
    'harvest_poppy': 'poppy',
    'harvest_mushroom': 'red_mushroom',
    'harvest_ephedra': 'quartz',
    'harvest_coca': 'vine',
    'harvest_sassafras': 'magenta_dye',
    'harvest_ergot': 'spore_blossom',
    'harvest_salvia': 'lilac',
    'harvest_resin': 'bone_meal',
    'harvest_tobacco': 'dried_kelp',
    'harvest_coffee': 'cocoa_beans',
}

MATERIALS.update(EXTRA_ITEMS)

TINTED_FALLBACKS = {
    'fern': [{'type': 'minecraft:constant', 'value': -12012264}],
    'large_fern': [{'type': 'minecraft:constant', 'value': -12012264}],
    'vine': [{'type': 'minecraft:constant', 'value': -12012264}],
    'potion': [{'type': 'minecraft:potion', 'default': -13083194}],
}

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
    if name == 'farm_controller':
        rect((3,3,12,14),'#513b29'); rect((4,4,11,13),'#ac7843')
        for x in (5,8,11): draw.line((x,4,x,13), fill='#78512f')
        rect((3,5,12,6),'#8da1a0'); rect((3,11,12,12),'#8da1a0')
        rect((6,7,10,10),'#254e40'); rect((7,8,9,9),'#8ed76d')
    elif name == 'farm_area_wand':
        draw.line((3,13,11,5),fill='#45322e',width=3)
        draw.line((3,12,10,5),fill='#aa8753')
        draw.polygon([(8,3),(11,1),(14,4),(11,7)],fill='#2c797c')
        draw.polygon([(9,3),(11,2),(13,4),(11,6)],fill='#73e6d2')
        draw.point((12,2), fill='#effff3'); draw.point((6,3),fill='#b0efff')
    elif name == 'farm_work_contract':
        rect((3,2,12,13),'#946e48'); rect((4,2,11,12),'#efdfb7')
        rect((2,1,11,2),'#d8b985'); rect((4,12,13,14),'#d8b985')
        for y in (4,6,8): draw.line((5,y,10,y),fill='#978364')
        draw.ellipse((8,9,12,13), fill='#9654a4'); draw.point((10,10),fill='#e0ace0')
    elif name.startswith('seed_') or name == 'cannabis_plant':
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
    if name.startswith('farm_'):
        return image
    # Draw silhouettes on the original grid, then add a finer pixel layer for surface detail.
    return add_material_detail(image.resize((32, 32), Image.Resampling.NEAREST), name)

shutil.rmtree(ROOT / 'assets/minecraft', ignore_errors=True)
by_material = defaultdict(list)

for index, item in enumerate(IDS):
    cmd = 1001 + index
    base = ROOT / 'assets/lumidrugs'
    texture = base / f'textures/item/{item}.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    # Retain custom artwork without replacing any vanilla textures.
    artwork = {'cannabis_plant': 'fern', 'joint': 'joint', 'cart': 'cart', 'bong': 'bong'}.get(item)
    if item in VARIANTS:
        flavor_icon(*VARIANTS[item], icon).save(texture)
    elif artwork:
        with Image.open(ROOT / f'tools/artwork/{artwork}.png') as image:
            image.save(texture)
    else:
        icon(item).save(texture)
    write_json(base / f'items/{item}.json', {'model': {'type': 'minecraft:model', 'model': f'lumidrugs:item/{item}'}})
    write_json(base / f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'lumidrugs:item/{item}'}})
    by_material[MATERIALS[VARIANTS[item][0] if item in VARIANTS else item]].append((cmd, item))

for material, entries in sorted(by_material.items()):
    fallback = {'type': 'minecraft:model', 'model': f'minecraft:item/{material}'}
    if material in TINTED_FALLBACKS:
        fallback['tints'] = TINTED_FALLBACKS[material]
    write_json(ROOT / f'assets/minecraft/items/{material}.json', {
        'model': {
            'type': 'minecraft:range_dispatch',
            'property': 'minecraft:custom_model_data',
            'entries': [
                {
                    'threshold': cmd,
                    'model': {'type': 'minecraft:model', 'model': f'lumidrugs:item/{item}'},
                }
                for cmd, item in entries
            ],
            'fallback': fallback,
        }
    })

write_json(ROOT / 'pack.mcmeta', {'pack': {'min_format':69,'max_format':[97, 1],'description':'LumiDrugs — crops, ingredients, flavors, drugs and cures (1.21.9–26.3)'}})
# Keep source artwork/tools outside the distributable pack.
for archive in (ROOT.parent / 'LumiDrugs-resourcepack.zip', ROOT / 'LumiDrugs.zip'):
    with ZipFile(archive, 'w', ZIP_DEFLATED) as out:
        out.write(ROOT / 'pack.mcmeta', 'pack.mcmeta')
        for path in sorted((ROOT / 'assets').rglob('*')):
            if path.is_file(): out.write(path, path.relative_to(ROOT))
print(f'Built {len(IDS)} item models and textures with custom_model_data (1001–{1000 + len(IDS)})')

# Review sheet stays outside the distributed pack.
cell_w, cell_h = 88, 80
sheet = Image.new('RGB', (120 + cell_w * len(FLAVORS['flavors']), 32 + cell_h * len(FLAVORS['products'])), '#20232b')
draw = ImageDraw.Draw(sheet)
for column, flavor in enumerate(FLAVORS['flavors']):
    draw.text((125 + column * cell_w, 10), flavor.title(), fill='#eeeeee')
for row, product in enumerate(FLAVORS['products']):
    draw.text((5, 60 + row * cell_h), product.replace('_', ' '), fill='#eeeeee')
    for column, flavor in enumerate(FLAVORS['flavors']):
        item = f'{product}_flavor_{flavor}'
        with Image.open(ROOT / f'assets/lumidrugs/textures/item/{item}.png') as image:
            enlarged = image.resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(enlarged, (130 + column * cell_w, 38 + row * cell_h), enlarged)
sheet.save(ROOT / 'tools/flavor-preview.png')
