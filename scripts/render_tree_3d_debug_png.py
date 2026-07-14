from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


COLORS = {
    "T": (112, 73, 38),
    "B": (122, 80, 43),
    "L": (44, 132, 67),
    "R": (94, 58, 31),
    "V": (38, 116, 45),
    "U": (94, 150, 80),
    "F": (106, 66, 35),
    "S": (70, 150, 70),
    "O": (230, 190, 60),
}


def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def project(x: int, y: int, z: int, scale: int) -> tuple[float, float]:
    return ((x - z) * scale * 0.62, (x + z) * scale * 0.34 - y * scale * 0.72)


def draw_cube(draw: ImageDraw.ImageDraw, x: int, y: int, z: int, color: tuple[int, int, int], ox: float, oy: float, scale: int) -> None:
    p000 = project(x, y, z, scale)
    p100 = project(x + 1, y, z, scale)
    p010 = project(x, y + 1, z, scale)
    p110 = project(x + 1, y + 1, z, scale)
    p001 = project(x, y, z + 1, scale)
    p101 = project(x + 1, y, z + 1, scale)
    p011 = project(x, y + 1, z + 1, scale)
    p111 = project(x + 1, y + 1, z + 1, scale)

    def shift(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
        return [(px + ox, py + oy) for px, py in points]

    draw.polygon(shift([p010, p110, p111, p011]), fill=shade(color, 1.15))
    draw.polygon(shift([p001, p011, p111, p101]), fill=shade(color, 0.78))
    draw.polygon(shift([p100, p101, p111, p110]), fill=shade(color, 0.92))
    edge = shade(color, 0.52)
    draw.line(shift([p010, p110, p111, p011, p010]), fill=edge, width=1)
    draw.line(shift([p001, p011, p111, p101, p001]), fill=edge, width=1)
    draw.line(shift([p100, p101, p111, p110, p100]), fill=edge, width=1)


def parse_debug(path: Path) -> tuple[dict[str, str], list[tuple[int, list[str]]]]:
    text = path.read_text(encoding="utf-8", errors="replace").splitlines()
    meta: dict[str, str] = {}
    layers: list[tuple[int, list[str]]] = []

    for key in ("species", "stage", "base", "intent", "target-height", "stage-visible-height", "sample"):
        pattern = re.compile(rf"^\s*{re.escape(key)}:\s*(.+?)\s*$")
        for line in text:
            match = pattern.match(line)
            if match:
                meta[key] = match.group(1).strip("'\"")
                break

    in_plan = False
    current_y: int | None = None
    current_rows: list[str] = []
    in_rows = False
    for raw in text:
        if raw.startswith("plan-3d:"):
            in_plan = True
            continue
        if in_plan and raw.startswith("live-3d:"):
            break
        if not in_plan:
            continue
        y_match = re.match(r"\s*-\s*y:\s*(-?\d+)", raw)
        if y_match:
            if current_y is not None and current_rows:
                layers.append((current_y, current_rows))
            current_y = int(y_match.group(1))
            current_rows = []
            in_rows = False
            continue
        if re.match(r"\s*rows:\s*$", raw):
            in_rows = True
            continue
        if in_rows:
            row_match = re.match(r"\s*-\s*'([^']*)'", raw)
            if row_match:
                current_rows.append(row_match.group(1))
                continue
            if raw.strip() and not raw.lstrip().startswith("-"):
                in_rows = False
    if current_y is not None and current_rows:
        layers.append((current_y, current_rows))
    return meta, layers


def render(meta: dict[str, str], layers: list[tuple[int, list[str]]], output: Path) -> None:
    blocks: list[tuple[int, int, int, str]] = []
    if not layers:
        raise SystemExit("No plan-3d layers found.")
    min_world_y = min(y for y, _ in layers)
    width = max(len(row) for _, rows in layers for row in rows)
    depth = max(len(rows) for _, rows in layers)
    center_x = width // 2
    center_z = depth // 2
    for world_y, rows in layers:
        rel_y = world_y - min_world_y
        for z, row in enumerate(rows):
            for x, char in enumerate(row):
                role = char.upper()
                if role in COLORS:
                    blocks.append((x - center_x, rel_y, z - center_z, role))

    image_w, image_h = 1500, 950
    scale = max(7, min(15, int(720 / max(20, width + depth + len(layers)))))
    projected = [project(x, y, z, scale) for x, y, z, _ in blocks]
    min_px = min(px for px, _ in projected)
    max_px = max(px for px, _ in projected)
    min_py = min(py for _, py in projected)
    max_py = max(py for _, py in projected)
    ox = image_w / 2 - (min_px + max_px) / 2
    oy = image_h - 80 - max_py
    if min_py + oy < 90:
        oy += 90 - (min_py + oy)

    image = Image.new("RGB", (image_w, image_h), (239, 243, 246))
    draw = ImageDraw.Draw(image)
    try:
        title_font = ImageFont.truetype("arial.ttf", 28)
        text_font = ImageFont.truetype("arial.ttf", 16)
    except OSError:
        title_font = ImageFont.load_default()
        text_font = ImageFont.load_default()

    title = f"{meta.get('species', 'tree')} {meta.get('stage', 'stage')} debug candidate"
    subtitle = (
        f"base={meta.get('base', '?')} intent={meta.get('intent', '?')} "
        f"target={meta.get('target-height', '?')} visible={meta.get('stage-visible-height', '?')}"
    )
    draw.text((24, 18), title, fill=(28, 38, 45), font=title_font)
    draw.text((24, 54), subtitle, fill=(71, 83, 92), font=text_font)
    if meta.get("sample"):
        sample = meta["sample"]
        if len(sample) > 150:
            sample = sample[:147] + "..."
        draw.text((24, 78), "sample=" + sample, fill=(71, 83, 92), font=text_font)

    # Natural render order for the voxel illusion: low blocks and back rows first.
    for x, y, z, role in sorted(blocks, key=lambda item: (item[1], item[0] + item[2], item[2], item[0])):
        draw_cube(draw, x, y, z, COLORS[role], ox, oy, scale)

    legend_x = image_w - 350
    legend_y = 24
    for index, (role, label) in enumerate([
        ("T", "trunk"), ("B", "branch"), ("L", "canopy"), ("R", "root"),
        ("V", "vine"), ("U", "ground detail"), ("F", "fallen log"), ("S", "sapling")
    ]):
        y = legend_y + index * 24
        draw.rectangle((legend_x, y + 3, legend_x + 16, y + 19), fill=COLORS[role])
        draw.text((legend_x + 24, y), f"{role} = {label}", fill=(45, 55, 62), font=text_font)

    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: render_tree_3d_debug_png.py <tree-evolution-3dDebug.yml> <output.png>")
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    meta, layers = parse_debug(source)
    render(meta, layers, output)
    print(output)


if __name__ == "__main__":
    main()
