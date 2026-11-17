#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="/home/tristanroux/Projets/spotify-fast-android"
DESIGN_DIR="$PROJECT_DIR/design"
ICONS_DIR="$DESIGN_DIR/icons"
PREVIEWS_DIR="$DESIGN_DIR/previews"
ANIM_DIR="$DESIGN_DIR/animations"
RES_DRAWABLE_DIR="$PROJECT_DIR/app/src/main/res/drawable"
KOTLIN_ICONS_FILE="$PROJECT_DIR/app/src/main/java/com/tristan/spotifyfast/ui/icons/SpotifyIcons.kt"

FIGCRAFT_CLI="${FIGCRAFT_CLI:-$HOME/.local/bin/figcraft-cli}"
VECTORCRAFT_CLI="${VECTORCRAFT_CLI:-$HOME/.local/bin/vectorcraft-cli}"
ANICRAFT_CLI="${ANICRAFT_CLI:-$HOME/.local/bin/anicraft-cli}"

mkdir -p "$ICONS_DIR" "$PREVIEWS_DIR" "$ANIM_DIR/frames" "$RES_DRAWABLE_DIR" "$(dirname "$KOTLIN_ICONS_FILE")"

echo "=== [1/4] FigCraft: generating design/spotify-fast.figcraft & preview ==="
"$FIGCRAFT_CLI" --no-system-fonts run "$DESIGN_DIR/figcraft_script.json" \
  --out "$DESIGN_DIR/spotify-fast.figcraft" >/dev/null
"$FIGCRAFT_CLI" --no-system-fonts render "$DESIGN_DIR/spotify-fast.figcraft" \
  --out "$PREVIEWS_DIR/overview.png" >/dev/null
"$FIGCRAFT_CLI" info "$DESIGN_DIR/spotify-fast.figcraft"

echo "=== [2/4] VectorCraft: generating 24dp SVG icons ==="
generate_svg_icon() {
  local name="$1"
  local path_data="$2"
  local color="${3:-#FFFFFF}"
  local out_svg="$ICONS_DIR/$name.svg"
  local cmds_file
  cmds_file="$(mktemp /tmp/vc_icon.XXXXXX.json)"
  cat > "$cmds_file" <<EOF
[
  {"command": "file.new", "params": {"title": "$name", "width": 24, "height": 24, "units": "Points"}},
  {"command": "path.create", "params": {"d": "$path_data", "closed": true}},
  {"command": "paint.setFill", "params": {"color": "$color"}},
  {"command": "paint.setStroke", "params": {"none": true}}
]
EOF
  "$VECTORCRAFT_CLI" --safe-mode run --commands "$cmds_file" --export "$out_svg" >/dev/null
  rm -f "$cmds_file"
}

generate_svg_icon "home" "M12 3 L3 10 L3 21 L9 21 L9 14 L15 14 L15 21 L21 21 L21 10 Z"
generate_svg_icon "search" "M10.5 3 C6.36 3 3 6.36 3 10.5 C3 14.64 6.36 18 10.5 18 C12.2 18 13.78 17.43 15.04 16.46 L20.29 21.71 L21.71 20.29 L16.46 15.04 C17.43 13.78 18 12.2 18 10.5 C18 6.36 14.64 3 10.5 3 Z M10.5 5 C13.54 5 16 7.46 16 10.5 C16 13.54 13.54 16 10.5 16 C7.46 16 5 13.54 5 10.5 C5 7.46 7.46 5 10.5 5 Z"
generate_svg_icon "library" "M4 4 L6 4 L6 20 L4 20 Z M9 4 L11 4 L11 20 L9 20 Z M14 4.5 L16 4 L20.5 19.5 L18.5 20 Z"
generate_svg_icon "heart" "M12 21.35 L10.55 20.03 C5.4 15.36 2 12.28 2 8.5 C2 5.42 4.42 3 7.5 3 C9.24 3 10.91 3.81 12 5.09 C13.09 3.81 14.76 3 16.5 3 C19.58 3 22 5.42 22 8.5 C22 12.28 18.6 15.36 13.45 20.04 L12 21.35 Z" "#1DB954"
generate_svg_icon "shuffle" "M10.59 9.17 L5.41 4 L4 5.41 L9.17 10.58 L10.59 9.17 Z M14.5 4 L16.54 6.04 L4 18.59 L5.41 20 L17.96 7.46 L20 9.5 L20 4 L14.5 4 Z M14.83 13.41 L13.42 14.82 L16.55 17.95 L14.5 20 L20 20 L20 14.5 L17.96 16.54 L14.83 13.41 Z"
generate_svg_icon "repeat" "M7 7 L17 7 L17 10 L21 6 L17 2 L17 5 L5 5 L5 11 L7 11 L7 7 Z M17 17 L7 17 L7 14 L3 18 L7 22 L7 19 L19 19 L19 13 L17 13 L17 17 Z"
generate_svg_icon "play" "M8 5 L19 12 L8 19 Z"
generate_svg_icon "pause" "M6 5 L10 5 L10 19 L6 19 Z M14 5 L18 5 L18 19 L14 19 Z"
generate_svg_icon "queue" "M3 6 L15 6 L15 8 L3 8 Z M3 10 L15 10 L15 12 L3 12 Z M3 14 L11 14 L11 16 L3 16 Z M17 6 L17 14.18 C16.69 14.07 16.35 14 16 14 C14.34 14 13 15.34 13 17 C13 18.66 14.34 20 16 20 C17.66 20 19 18.66 19 17 L19 8 L22 8 L22 6 L17 6 Z"
generate_svg_icon "devices" "M4 6 L22 6 L22 4 L4 4 C2.9 4 2 4.9 2 6 L2 17 L0 17 L0 20 L14 20 L14 17 L4 17 L4 6 Z M23 8 L17 8 C16.45 8 16 8.45 16 9 L16 19 C16 19.55 16.45 20 17 20 L23 20 C23.55 20 24 19.55 24 19 L24 9 C24 8.45 23.55 8 23 8 Z M22 17 L18 17 L18 10 L22 10 L22 17 Z"
generate_svg_icon "lyrics" "M12 3 L12 13.55 C11.41 13.21 10.73 13 10 13 C7.79 13 6 14.79 6 17 C6 19.21 7.79 21 10 21 C12.21 21 14 19.21 14 17 L14 7 L18 7 L18 3 L12 3 Z"
generate_svg_icon "speed" "M10 8 L10 16 L17 12 L10 8 Z M12 2 C6.48 2 2 6.48 2 12 C2 17.52 6.48 22 12 22 C17.52 22 22 17.52 22 12 C22 6.48 17.52 2 12 2 Z M12 20 C7.59 20 4 16.41 4 12 C4 7.59 7.59 4 12 4 C16.41 4 20 7.59 20 12 C20 16.41 16.41 20 12 20 Z"

"$VECTORCRAFT_CLI" info "$ICONS_DIR/home.svg"

echo "=== [3/4] Transpiling VectorCraft SVGs -> Kotlin ImageVector & Android VectorDrawable ==="
python3 - "$ICONS_DIR" "$KOTLIN_ICONS_FILE" "$RES_DRAWABLE_DIR" <<'PYEOF'
import os
import re
import sys
import xml.etree.ElementTree as ET

icons_dir, kt_out, res_dir = sys.argv[1], sys.argv[2], sys.argv[3]

def extract_paths(svg_file):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    paths = []
    for elem in root.iter():
        tag = elem.tag.split('}')[-1]
        if tag == 'path':
            d = elem.attrib.get('d', '').strip()
            if d:
                paths.append(d)
        elif tag in ('polygon', 'polyline'):
            pts_raw = elem.attrib.get('points', '').strip()
            nums = re.findall(r'[-+]?\d*\.?\d+', pts_raw)
            if len(nums) >= 4:
                cmds = [f'M{nums[0]} {nums[1]}']
                for j in range(2, len(nums) - 1, 2):
                    cmds.append(f'L{nums[j]} {nums[j+1]}')
                if tag == 'polygon':
                    cmds.append('Z')
                paths.append(' '.join(cmds))
        elif tag == 'rect':
            x = float(elem.attrib.get('x', 0))
            y = float(elem.attrib.get('y', 0))
            w = float(elem.attrib.get('width', 0))
            h = float(elem.attrib.get('height', 0))
            if w > 0 and h > 0:
                paths.append(f'M{x} {y} L{x+w} {y} L{x+w} {y+h} L{x} {y+h} Z')
    return paths

def d_to_compose_calls(d):
    tokens = re.findall(r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?', d)
    lines = []
    i = 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if re.match(r'^[A-Za-z]$', t):
            cmd = t
            i += 1
            if cmd in ('Z', 'z'):
                lines.append('close()')
            continue
        if cmd == 'M':
            lines.append(f'moveTo({float(tokens[i])}f, {float(tokens[i+1])}f)')
            i += 2
            cmd = 'L'
        elif cmd == 'm':
            lines.append(f'moveToRelative({float(tokens[i])}f, {float(tokens[i+1])}f)')
            i += 2
            cmd = 'l'
        elif cmd == 'L':
            lines.append(f'lineTo({float(tokens[i])}f, {float(tokens[i+1])}f)')
            i += 2
        elif cmd == 'l':
            lines.append(f'lineToRelative({float(tokens[i])}f, {float(tokens[i+1])}f)')
            i += 2
        elif cmd == 'H':
            lines.append(f'horizontalLineTo({float(tokens[i])}f)')
            i += 1
        elif cmd == 'h':
            lines.append(f'horizontalLineToRelative({float(tokens[i])}f)')
            i += 1
        elif cmd == 'V':
            lines.append(f'verticalLineTo({float(tokens[i])}f)')
            i += 1
        elif cmd == 'v':
            lines.append(f'verticalLineToRelative({float(tokens[i])}f)')
            i += 1
        elif cmd == 'C':
            lines.append(
                f'curveTo({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f, '
                f'{float(tokens[i+4])}f, {float(tokens[i+5])}f)'
            )
            i += 6
        elif cmd == 'c':
            lines.append(
                f'curveToRelative({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f, '
                f'{float(tokens[i+4])}f, {float(tokens[i+5])}f)'
            )
            i += 6
        elif cmd == 'Q':
            lines.append(
                f'quadTo({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f)'
            )
            i += 4
        elif cmd == 'q':
            lines.append(
                f'quadToRelative({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f)'
            )
            i += 4
        elif cmd == 'S':
            lines.append(
                f'reflectiveCurveTo({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f)'
            )
            i += 4
        elif cmd == 's':
            lines.append(
                f'reflectiveCurveToRelative({float(tokens[i])}f, {float(tokens[i+1])}f, '
                f'{float(tokens[i+2])}f, {float(tokens[i+3])}f)'
            )
            i += 4
        elif cmd == 'A':
            lines.append(
                f'arcTo({float(tokens[i])}f, {float(tokens[i+1])}f, {float(tokens[i+2])}f, '
                f'{"true" if int(float(tokens[i+3])) else "false"}, '
                f'{"true" if int(float(tokens[i+4])) else "false"}, '
                f'{float(tokens[i+5])}f, {float(tokens[i+6])}f)'
            )
            i += 7
        else:
            i += 1
    return lines

svg_specs = [
    ("Home", "home.svg"),
    ("Search", "search.svg"),
    ("Library", "library.svg"),
    ("Heart", "heart.svg"),
    ("Shuffle", "shuffle.svg"),
    ("Repeat", "repeat.svg"),
    ("Play", "play.svg"),
    ("Pause", "pause.svg"),
    ("Queue", "queue.svg"),
    ("Devices", "devices.svg"),
    ("Lyrics", "lyrics.svg"),
    ("Speed", "speed.svg"),
]

kt_parts = [
    "package com.tristan.spotifyfast.ui.icons",
    "",
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.graphics.PathFillType",
    "import androidx.compose.ui.graphics.SolidColor",
    "import androidx.compose.ui.graphics.vector.ImageVector",
    "import androidx.compose.ui.graphics.vector.path",
    "import androidx.compose.ui.unit.dp",
    "",
    "/**",
    " * Vector icons compiled from VectorCraft SVGs in design/icons/.",
    " */",
    "object SpotifyIcons {",
]

for prop_name, filename in svg_specs:
    path_list = extract_paths(os.path.join(icons_dir, filename))
    backing = f"_{prop_name.lower()}"
    kt_parts.append(f"    private var {backing}: ImageVector? = null")
    kt_parts.append(f"    val {prop_name}: ImageVector")
    kt_parts.append(f"        get() = {backing} ?: ImageVector.Builder(")
    kt_parts.append(f'            name = "{prop_name}",')
    kt_parts.append("            defaultWidth = 24.dp,")
    kt_parts.append("            defaultHeight = 24.dp,")
    kt_parts.append("            viewportWidth = 24f,")
    kt_parts.append("            viewportHeight = 24f,")
    kt_parts.append("        ).apply {")
    for p in path_list:
        kt_parts.append("            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {")
        for call in d_to_compose_calls(p):
            kt_parts.append(f"                {call}")
        kt_parts.append("            }")
    kt_parts.append(f"        }}.build().also {{ {backing} = it }}")
    kt_parts.append("")

extra_icons = {
    "SkipNext": "M6 18 L14.5 12 L6 6 L6 18 Z M16 6 L16 18 L18 18 L18 6 L16 6 Z",
    "SkipPrevious": "M6 6 L8 6 L8 18 L6 18 Z M9.5 12 L18 18 L18 6 L9.5 12 Z",
    "Back": "M20 11 L7.83 11 L13.42 5.41 L12 4 L4 12 L12 20 L13.41 18.59 L7.83 13 L20 13 L20 11 Z",
    "Replay15": "M12 5 L12 1 L7 6 L12 11 L12 7 C15.31 7 18 9.69 18 13 C18 16.31 15.31 19 12 19 C8.69 19 6 16.31 6 13 L4 13 C4 17.42 7.58 21 12 21 C16.42 21 20 17.42 20 13 C20 8.58 16.42 5 12 5 Z",
    "Forward15": "M12 5 L12 1 L17 6 L12 11 L12 7 C8.69 7 6 9.69 6 13 C6 16.31 8.69 19 12 19 C15.31 19 18 16.31 18 13 L20 13 C20 17.42 16.42 21 12 21 C7.58 21 4 17.42 4 13 C4 8.58 7.58 5 12 5 Z",
    "HeartOutline": "M16.5 3 C14.76 3 13.09 3.81 12 5.09 C10.91 3.81 9.24 3 7.5 3 C4.42 3 2 5.42 2 8.5 C2 12.28 5.4 15.36 10.55 20.04 L12 21.35 L13.45 20.03 C18.6 15.36 22 12.28 22 8.5 C22 5.42 19.58 3 16.5 3 Z M12.1 18.55 L12 18.65 L11.9 18.55 C7.14 14.24 4 11.39 4 8.5 C4 6.5 5.5 5 7.5 5 C9.04 5 10.54 5.99 11.07 7.36 L12.94 7.36 C13.46 5.99 14.96 5 16.5 5 C18.5 5 20 6.5 20 8.5 C20 11.39 16.86 14.24 12.1 18.55 Z",
    "Add": "M19 13 L13 13 L13 19 L11 19 L11 13 L5 13 L5 11 L11 11 L11 5 L13 5 L13 11 L19 11 L19 13 Z",
    "Check": "M9 16.17 L4.83 12 L3.41 13.41 L9 19 L21 7 L19.59 5.59 L9 16.17 Z",
    "MoreVert": "M12 8 C13.1 8 14 7.1 14 6 C14 4.9 13.1 4 12 4 C10.9 4 10 4.9 10 6 C10 7.1 10.9 8 12 8 Z M12 10 C10.9 10 10 10.9 10 12 C10 13.1 10.9 14 12 14 C13.1 14 14 13.1 14 12 C14 10.9 13.1 10 12 10 Z M12 16 C10.9 16 10 16.9 10 18 C10 19.1 10.9 20 12 20 C13.1 20 14 19.1 14 18 C14 16.9 13.1 16 12 16 Z",
    "Close": "M19 6.41 L17.59 5 L12 10.59 L6.41 5 L5 6.41 L10.59 12 L5 17.59 L6.41 19 L12 13.41 L17.59 19 L19 17.59 L13.41 12 Z",
    "Grid": "M3 3 L11 3 L11 11 L3 11 Z M13 3 L21 3 L21 11 L13 11 Z M3 13 L11 13 L11 21 L3 21 Z M13 13 L21 13 L21 21 L13 21 Z",
    "List": "M3 5 L21 5 L21 7 L3 7 Z M3 11 L21 11 L21 13 L3 13 Z M3 17 L21 17 L21 19 L3 19 Z",
}

for prop_name, d_str in extra_icons.items():
    backing = f"_{prop_name.lower()}"
    kt_parts.append(f"    private var {backing}: ImageVector? = null")
    kt_parts.append(f"    val {prop_name}: ImageVector")
    kt_parts.append(f"        get() = {backing} ?: ImageVector.Builder(")
    kt_parts.append(f'            name = "{prop_name}",')
    kt_parts.append("            defaultWidth = 24.dp,")
    kt_parts.append("            defaultHeight = 24.dp,")
    kt_parts.append("            viewportWidth = 24f,")
    kt_parts.append("            viewportHeight = 24f,")
    kt_parts.append("        ).apply {")
    kt_parts.append("            path(fill = SolidColor(Color.White)) {")
    for call in d_to_compose_calls(d_str):
        kt_parts.append(f"                {call}")
    kt_parts.append("            }")
    kt_parts.append(f"        }}.build().also {{ {backing} = it }}")
    kt_parts.append("")

kt_parts.append("}")

with open(kt_out, "w", encoding="utf-8") as f:
    f.write("\n".join(kt_parts))

# Android VectorDrawables for app icon and Media3 notification
launcher_xml = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#121212"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#1DB954"
        android:pathData="M54,14 C31.91,14 14,31.91 14,54 C14,76.09 31.91,94 54,94 C76.09,94 94,76.09 94,54 C94,31.91 76.09,14 54,14 Z" />
    <path
        android:fillColor="#121212"
        android:pathData="M34,42 L40,42 L40,68 L34,68 Z M46,32 L52,32 L52,74 L46,74 Z M58,38 L64,38 L64,70 L58,70 Z M70,48 L76,48 L76,64 L70,64 Z" />
</vector>
"""
with open(os.path.join(res_dir, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
    f.write(launcher_xml)

lyrics_paths = extract_paths(os.path.join(icons_dir, "lyrics.svg"))
notif_xml = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="{lyrics_paths[0] if lyrics_paths else 'M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z'}" />
</vector>
"""
with open(os.path.join(res_dir, "ic_notification_music.xml"), "w", encoding="utf-8") as f:
    f.write(notif_xml)

print(f"Generated {kt_out} and VectorDrawables in {res_dir}")
PYEOF

echo "=== [4/4] AniCraft: generating equalizer.anicraft & PNG sequence ==="
rm -f "$ANIM_DIR/equalizer.anicraft" "$ANIM_DIR/equalizer.tmp.anicraft"
"$ANICRAFT_CLI" new --out "$ANIM_DIR/equalizer.tmp.anicraft" --width 96 --height 96 --fps 60 >/dev/null
ANICRAFT_SCRIPT="$(mktemp /tmp/anicraft_eq.XXXXXX.json)"
cat > "$ANICRAFT_SCRIPT" <<'EOF'
[
  {"cmd": "draw.rect", "params": {"x": 12, "y": 48, "w": 12, "h": 36, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 32, "y": 24, "w": 12, "h": 60, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 52, "y": 36, "w": 12, "h": 48, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 72, "y": 54, "w": 12, "h": 30, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "timeline.insert_keyframe", "params": {"frame": 4}},
  {"cmd": "draw.erase_all", "params": {}},
  {"cmd": "draw.rect", "params": {"x": 12, "y": 26, "w": 12, "h": 58, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 32, "y": 52, "w": 12, "h": 32, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 52, "y": 20, "w": 12, "h": 64, "radius": 4, "fill": "#1DB954"}},
  {"cmd": "draw.rect", "params": {"x": 72, "y": 40, "w": 12, "h": 44, "radius": 4, "fill": "#1DB954"}}
]
EOF
"$ANICRAFT_CLI" run "$ANIM_DIR/equalizer.tmp.anicraft" "$ANICRAFT_SCRIPT" --save "$ANIM_DIR/equalizer.anicraft" >/dev/null
rm -f "$ANICRAFT_SCRIPT" "$ANIM_DIR/equalizer.tmp.anicraft"
rm -rf "$ANIM_DIR/frames" && mkdir -p "$ANIM_DIR/frames"
"$ANICRAFT_CLI" render-seq "$ANIM_DIR/equalizer.anicraft" --out-dir "$ANIM_DIR/frames" --from 1 --to 4 >/dev/null
"$ANICRAFT_CLI" info "$ANIM_DIR/equalizer.anicraft"
