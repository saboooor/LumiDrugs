"""Deterministic, palette-stepped material detail for the pack's native pixel sprites."""
import hashlib
import random


def material_for(name):
    if name in ('meth', 'cocaine', 'nicotine', 'caffeine', 'harvest_ephedra'):
        return 'crystal' if name == 'meth' else 'powder'
    if name in ('joint', 'cigarette', 'lsd', 'farm_work_contract'):
        return 'paper'
    if name in ('blunt', 'cigar', 'tobacco', 'chewing_tobacco', 'heroin'):
        return 'fiber'
    if name in ('molly', 'suboxone', 'fent'):
        return 'tablet'
    if name in ('edible', 'infused_dough'):
        return 'crumb'
    if name in ('vape', 'cart'):
        return 'metal'
    if name in ('cannabis_extract', 'vape_liquid', 'cbd', 'glue', 'energy_drink', 'bong', 'harvest_resin'):
        return 'glass'
    return 'botanical'


def add_material_detail(source, name, changed=None, seed_name=None):
    """Keep every alpha pixel and silhouette; add light, clustered grain and material marks.

    `changed` restricts detail to new flavor overlays, leaving the base and tiny seals intact.
    No unique batch randomness: the same item always rebuilds to exactly the same pixels.
    """
    image = source.convert('RGBA').copy()
    width, height = image.size
    pixels = source.convert('RGBA').load()
    out = image.load()
    rng = random.Random(int.from_bytes(hashlib.sha256((seed_name or name).encode()).digest()[:8], 'big'))
    clusters = {(x, y): rng.choice((-12, -6, 0, 6, 12))
                for y in range((height + 1) // 2) for x in range((width + 1) // 2)}
    material = material_for(name)
    box = image.getbbox()
    if box is None:
        return image
    left, top, right, bottom = box

    def opaque(x, y):
        return 0 <= x < width and 0 <= y < height and pixels[x, y][3] > 0

    for y in range(height):
        for x in range(width):
            r, g, b, alpha = pixels[x, y]
            if not alpha or changed is not None and not changed(x, y):
                continue
            light = (r + g + b) / 3
            edge = not all(opaque(x + dx, y + dy) for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))
            # Keep the existing dark contour crisp rather than sprinkling highlights on it.
            if light < 65 and edge:
                continue
            px = (x - left) / max(1, right - left - 1)
            py = (y - top) / max(1, bottom - top - 1)
            shade = 11 * (0.9 - px - py)
            grain = clusters[x // 2, y // 2]
            fine = rng.choice((-4, 0, 0, 4))
            if material in ('fiber', 'botanical'):
                # Diagonal veins, folded leaf tissue and tobacco/paper-wrapper fibers.
                grain += 8 if (x + 2 * y) % 9 in (0, 1) else -3
                if (2 * x - y) % 13 == 0:
                    grain -= 10
            elif material == 'paper':
                grain *= 0.5
                grain += -10 if (x + y) % 11 == 0 else 0
                grain += 7 if (2 * x + y) % 17 == 0 else 0
            elif material == 'crystal':
                # Hard directional facets and thin fracture planes.
                grain += 18 if (2 * x + y) % 13 < 4 else -10
                if (x - 2 * y) % 19 == 0:
                    grain += 18
            elif material == 'powder':
                grain += rng.choice((-16, -8, 0, 0, 8))
            elif material == 'crumb':
                # Porous baked crumbs with darker flecks and raised golden ridges.
                grain += -19 if rng.randrange(9) == 0 else 4
            elif material == 'tablet':
                grain *= 0.45
                shade *= 1.7
            elif material == 'metal':
                grain *= 0.35
                grain += 17 if 0.17 < px < 0.33 else -8 if 0.67 < px < 0.85 else 0
                fine += -5 if y % 4 == 0 else 0
            elif material == 'glass':
                grain *= 0.55
                grain += 13 if 0.16 < px < 0.29 else -7 if px > 0.75 else 0
                grain += 9 if (x + 3 * y) % 23 == 0 else 0
            if edge:
                shade += 5 if not opaque(x, y - 1) or not opaque(x - 1, y) else -9
            delta = round((shade + grain + fine) / 8) * 8
            delta = max(-32, min(24, delta))
            # Preserve the base palette: broad midtones, warm shadows, cooler highlights.
            warmth = 2 if delta < 0 and material in ('fiber', 'crumb', 'paper', 'botanical') else 0
            out[x, y] = (max(0, min(255, r + delta + warmth)),
                         max(0, min(255, g + delta)),
                         max(0, min(255, b + delta - warmth)), alpha)
    return image
