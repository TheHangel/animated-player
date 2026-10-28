"""Adds the mod's face animation pixels and joint caps to the game's default skins (Steve, Alex, Ari, ...).

For each tools/vanilla_skins/.../{wide,slim}/<name>.png (extracted from the Minecraft client jar), writes the same
skin with:
- face pixels in the hidden head area (x 24-31, y 0-3): eye whites and pupils read from the face (default skins all
  have their eyes on row 12: whites x 9 and 14, pupils x 10 and 13), eyebrows from the hair colour, darkened, and the
  teeth / inner mouth of the mod's 1.7.10 Steve. Pure white and black are nudged, as the mod ignores those colours.
- joint cap pixels (the w x 4 area after each limb's bottom face), from the limb's sides at the joint.
Usage: python3 tools/make_default_skins.py <out_dir>   (writes <out_dir>/wide/<name>.png and <out_dir>/slim/<name>.png)
"""
import glob
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import png  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'vanilla_skins/assets/minecraft/textures/entity/player')
EMPTY = (0, 0, 0, 0)


def usable(px):
    """The mod ignores pure white and pure black face pixels."""
    r, g, b, a = px
    if (r, g, b) == (255, 255, 255):
        return (250, 250, 250, 255)
    if (r, g, b) == (0, 0, 0):
        return (12, 12, 12, 255)
    return (r, g, b, 255)


def add_face(p):
    for y in range(4):
        for x in range(24, 32):
            p[y][x] = EMPTY
    hair = min((p[y][x] for y in (9, 10) for x in range(9, 15)), key=lambda c: c[0] + c[1] + c[2])
    brow = usable(tuple(int(c * 0.7) for c in hair[:3]) + (255,))
    for x in range(24, 27):
        p[0][x] = brow
    # Eyes on row 12 = the mod's middle eye position (zone row 1).
    p[1][27] = usable(p[12][9])
    p[1][28] = usable(p[12][10])
    p[1][30] = usable(p[12][13])
    p[1][31] = usable(p[12][14])
    # Teeth and inner mouth, as the 1.7.10 Steve.
    p[1][24], p[1][25] = (239, 239, 239, 255), (244, 244, 244, 255)
    p[2][24], p[2][25] = (29, 3, 3, 255), (35, 4, 4, 255)
    p[3][24], p[3][25] = (244, 244, 244, 255), (244, 244, 244, 255)


def add_caps(p, slim):
    limbs = [(40, 16, 3 if slim else 4), (32, 48, 3 if slim else 4), (0, 16, 4), (16, 48, 4)]
    for u, v, w in limbs:
        d = 4
        row = v + d + 6
        zone = u + d + 2 * w
        for i in range(w):
            for k in range(d):
                dists = {'front': k, 'back': d - 1 - k, 'minx': i, 'maxx': w - 1 - i}
                side = min(dists, key=dists.get)
                x = {'front': u + d + i, 'back': u + d + w + d + (w - 1 - i), 'minx': u + d - 1 - k, 'maxx': u + d + w + k}[side]
                p[v + k][zone + i] = p[row][x][:3] + (255,)


out_dir = sys.argv[1]
for path in sorted(glob.glob(os.path.join(SRC, '*', '*.png'))):
    model = os.path.basename(os.path.dirname(path))
    pixels = png.read(path)
    add_face(pixels)
    add_caps(pixels, model == 'slim')
    os.makedirs(os.path.join(out_dir, model), exist_ok=True)
    png.write(os.path.join(out_dir, model, os.path.basename(path)), pixels)
    print(model, os.path.basename(path))
