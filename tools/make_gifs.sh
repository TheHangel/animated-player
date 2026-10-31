#!/bin/zsh
# make_gifs.sh: turns the frames of the "gifs" debug scenario into looping transparent GIFs.
#   ./gradlew :neoforge:runDebugClient -Pscenario=gifs   (frames in neoforge/run/gif-frames/<name>/)
#   tools/make_gifs.sh                                  (GIFs in presentation/gifs/<name>.gif, each < 1 MB)
# Needs ffmpeg and gifsicle.
set -e
ROOT=${0:A:h:h}
FRAMES=$ROOT/neoforge/run/gif-frames
OUT=$ROOT/presentation/gifs
mkdir -p $OUT
for dir in $FRAMES/*(/); do
  name=${dir:t}
  for scale in 2 3 4; do
    # Half (then a third, a quarter) size, nearest neighbour to keep the pixels sharp; one palette for all frames.
    ffmpeg -loglevel error -y -framerate 20 -i $dir/f%03d.png \
      -vf "scale=iw/$scale:-1:flags=neighbor,split[a][b];[a]palettegen=reserve_transparent=1:stats_mode=full[p];[b][p]paletteuse=dither=none:alpha_threshold=128" \
      -loop 0 $OUT/$name.tmp.gif
    # Each frame replaces the previous one (transparent background), optimized.
    gifsicle -O3 --disposal background --loopcount=forever $OUT/$name.tmp.gif -o $OUT/$name.gif
    rm $OUT/$name.tmp.gif
    size=$(stat -f %z $OUT/$name.gif)
    (( size < 1000000 )) && break
  done
  echo "$name.gif: $(( size / 1024 )) KB (1/$scale size)"
done
