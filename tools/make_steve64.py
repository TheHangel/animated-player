"""Builds the mod's 64x64 Steve (textures/steve_64.png) from the 1.7.10 mod's 64x32 one (tools/steve_1.7.10.png).

Same conversion as the game for old skins (HttpTexture#processLegacySkin: left limbs are mirrored copies of the
right ones, second layer empty), plus the joint cap pixels of the left limbs (the 1.7.10 mod only had right limbs),
mirrored from the right ones. Face details (hidden head area) are kept as is.
Usage: python3 tools/make_steve64.py tools/steve_1.7.10.png common/src/main/resources/assets/animatedplayer/textures/steve_64.png
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import png  # noqa: E402

src = png.read(sys.argv[1])
assert len(src) == 32 and len(src[0]) == 64
out = [row[:] for row in src] + [[(0, 0, 0, 0)] * 64 for _ in range(32)]


def copy_rect(x, y, dx, dy, w, h, flip_x):
    pixels = [[out[y + j][x + i] for i in range(w)] for j in range(h)]
    for j in range(h):
        for i in range(w):
            out[y + dy + j][x + dx + (w - 1 - i if flip_x else i)] = pixels[j][i]


# HttpTexture#processLegacySkin.
for args in [(4, 16, 16, 32, 4, 4), (8, 16, 16, 32, 4, 4), (0, 20, 24, 32, 4, 12), (4, 20, 16, 32, 4, 12),
             (8, 20, 8, 32, 4, 12), (12, 20, 16, 32, 4, 12), (44, 16, -8, 32, 4, 4), (48, 16, -8, 32, 4, 4),
             (40, 20, 0, 32, 4, 12), (44, 20, -8, 32, 4, 12), (48, 20, -16, 32, 4, 12), (52, 20, -8, 32, 4, 12)]:
    copy_rect(*args, True)
# Joint caps: right leg (12, 16) -> left leg (28, 48); right arm (52, 16) -> left arm (44, 48).
copy_rect(12, 16, 16, 32, 4, 4, True)
copy_rect(52, 16, -8, 32, 4, 4, True)
png.write(sys.argv[2], out)
