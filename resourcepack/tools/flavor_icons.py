"""Native pixel-art drawing for flavored items; no vanilla texture replacements."""
from PIL import Image, ImageDraw, ImageColor
from surface_detail import add_material_detail


def flavor_icon(product, flavor, color, base_icon):
    image = base_icon(product).resize((32, 32), Image.Resampling.NEAREST)
    original = image.copy()
    draw = ImageDraw.Draw(image)
    rgb = ImageColor.getrgb(color)
    dark = tuple(int(c * .55) for c in rgb)
    light = tuple(min(255, c + 45) for c in rgb)
    if product in ('vape', 'cart'):
        draw.rectangle((13, 10, 19, 23), fill=dark)
        draw.rectangle((14, 11, 18, 21), fill=color)
        draw.line((14, 11, 14, 19), fill=light)
    elif product in ('joint', 'blunt', 'cigarette', 'cigar'):
        draw.line((15, 13, 19, 17), fill=dark, width=5)
        draw.line((16, 12, 20, 16), fill=color, width=3)
        draw.point((16, 12), fill=light)
    elif product == 'edible':
        draw.ellipse((6, 7, 25, 19), fill=dark)
        draw.ellipse((7, 7, 24, 17), fill=color)
        for x, y in ((10, 10), (17, 9), (21, 13), (13, 15)):
            draw.rectangle((x, y, x + 1, y + 1), fill=light)
    elif product == 'energy_drink':
        draw.rectangle((10, 15, 21, 23), fill=dark)
        draw.rectangle((11, 16, 20, 22), fill=color)
        draw.line(((17, 16), (14, 19), (18, 19), (15, 22)), fill='#fff1cc', width=1)
    elif product in ('tobacco', 'chewing_tobacco'):
        # A folded flavor-labeled pouch, keeping loose tobacco visible at the top.
        draw.rectangle((5, 11, 25, 28), fill='#302c32')
        draw.rectangle((6, 12, 24, 26), fill=dark)
        draw.rectangle((7, 16, 23, 24), fill=color)
        draw.line((7, 13, 23, 13), fill='#d9b77c', width=2)
        draw.line((8, 26, 22, 26), fill=light)

    # Small ingredient seal with a light rim, readable in inventory slots.
    draw.rectangle((21, 22, 30, 31), fill='#252733')
    draw.rectangle((22, 23, 29, 30), fill='#fff1ce')
    x, y = 22, 23
    if flavor in ('berry', 'grape'):
        for dx, dy in ((1, 2), (4, 2), (2, 5)):
            draw.ellipse((x+dx, y+dy, x+dx+2, y+dy+2), fill=color)
        draw.line((x+3, y, x+4, y+1), fill='#459148')
    elif flavor == 'cherry':
        draw.line(((x+1, y+4), (x+4, y), (x+6, y+4)), fill='#41783d')
        draw.ellipse((x, y+4, x+3, y+7), fill=color)
        draw.ellipse((x+4, y+4, x+7, y+7), fill=color)
    elif flavor in ('apple', 'peach'):
        draw.ellipse((x+1, y+2, x+6, y+7), fill=color)
        draw.line((x+3, y+2, x+4, y), fill='#4e743d')
        draw.point((x+2, y+3), fill=light)
        if flavor == 'peach': draw.line((x+4, y+3, x+4, y+6), fill=dark)
    elif flavor == 'melon':
        draw.polygon(((x, y+1), (x+7, y+1), (x+4, y+7)), fill='#3b8444')
        draw.polygon(((x+1, y+2), (x+6, y+2), (x+4, y+5)), fill='#ed777b')
        draw.point((x+3, y+3), fill='#3d3032')
    elif flavor == 'citrus':
        draw.ellipse((x, y, x+7, y+7), fill=color)
        draw.line((x+3, y+1, x+3, y+6), fill='#ffe3a2')
        draw.line((x+1, y+3, x+6, y+3), fill='#ffe3a2')
    elif flavor == 'mint':
        draw.polygon(((x, y+5), (x+2, y+1), (x+7, y), (x+6, y+5), (x+2, y+7)), fill='#459c66')
        draw.line((x+1, y+6, x+6, y+1), fill=color)
    elif flavor == 'honey':
        draw.polygon(((x+2, y), (x+5, y), (x+7, y+3), (x+5, y+7), (x+2, y+7), (x, y+3)), fill=color)
        draw.rectangle((x+2, y+2, x+4, y+4), outline='#916027')
    elif flavor == 'chocolate':
        draw.rectangle((x, y, x+6, y+7), fill=dark)
        for dx in (1, 4):
            for dy in (1, 4): draw.rectangle((x+dx, y+dy, x+dx+1, y+dy+1), fill=color)
    elif flavor == 'coffee':
        draw.ellipse((x, y, x+6, y+7), fill=color)
        draw.line(((x+4, y+1), (x+2, y+4), (x+3, y+6)), fill='#d5a377')
    elif flavor == 'cinnamon':
        draw.line((x+1, y+6, x+5, y+1), fill=dark, width=3)
        draw.line((x+3, y+7, x+7, y+2), fill=color, width=2)
    elif flavor == 'vanilla':
        draw.line((x+1, y+6, x+5, y+1), fill='#71513c', width=2)
        draw.line((x+4, y+4, x+7, y+4), fill='#e5c36d', width=3)
        draw.line((x+5, y+2, x+5, y+6), fill='#e5c36d')
        draw.point((x+5, y+4), fill='#fff9df')
    before, after = original.load(), image.load()
    # Texture only the new label/wrapper/frosting, preserving the already-detailed base.
    # Tiny ingredient seals stay crisp and readable at inventory scale.
    return add_material_detail(image, product,
                               changed=lambda x, y: not (x >= 21 and y >= 22) and before[x, y] != after[x, y],
                               seed_name=f'{product}_flavor_{flavor}')
