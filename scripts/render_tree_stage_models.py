from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "outputs" / "tree-shape-direction" / "staged-models"
PNG_OUT = OUT / "png"
OBJ_OUT = OUT / "obj"


@dataclass(frozen=True)
class TreeSpec:
    key: str
    title: str
    wood: str
    leaf: str
    note: str


SPECS = [
    TreeSpec("oak", "Oak", "oak_wood", "oak_leaf", "round crowns, uneven side limbs, chunky final form"),
    TreeSpec("birch", "Birch", "birch_wood", "birch_leaf", "height-first growth, slim trunk, restrained crown"),
    TreeSpec("spruce", "Spruce", "spruce_wood", "spruce_leaf", "stacked conifer shelves instead of wide random arms"),
    TreeSpec("jungle", "Jungle", "jungle_wood", "jungle_leaf", "large support trunk, high canopy, vines, big limbs"),
    TreeSpec("dark_oak", "Dark Oak", "dark_oak_wood", "dark_oak_leaf", "heavy low trunk with broad dense crown"),
    TreeSpec("acacia", "Acacia", "acacia_wood", "acacia_leaf", "angled trunk, forked frame, flat umbrella canopy"),
    TreeSpec("cherry", "Cherry", "cherry_wood", "cherry_leaf", "layered soft crown with covered branches"),
]

STAGES = ["small", "medium", "mature", "ancient"]

COLORS = {
    "oak_wood": (111, 72, 35),
    "birch_wood": (178, 159, 104),
    "spruce_wood": (75, 47, 27),
    "jungle_wood": (100, 63, 35),
    "dark_oak_wood": (61, 38, 22),
    "acacia_wood": (136, 68, 38),
    "cherry_wood": (116, 64, 55),
    "oak_leaf": (45, 133, 62),
    "birch_leaf": (86, 150, 61),
    "spruce_leaf": (28, 92, 65),
    "jungle_leaf": (36, 139, 72),
    "dark_oak_leaf": (27, 101, 52),
    "acacia_leaf": (64, 130, 70),
    "cherry_leaf": (220, 126, 171),
    "vine": (36, 110, 42),
    "grass": (92, 151, 82),
}


def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def put(blocks: dict[tuple[int, int, int], str], x: int, y: int, z: int, mat: str) -> None:
    blocks[(x, y, z)] = mat


def square_trunk(blocks: dict[tuple[int, int, int], str], mat: str, width: int, height: int, *, taper: bool = False) -> None:
    for y in range(height):
        layer_width = width
        if taper and width >= 4 and y > height * 0.68:
            layer_width = max(2, width - 1)
        if taper and width >= 5 and y > height * 0.84:
            layer_width = max(2, width - 2)
        offset = -(layer_width // 2)
        for x in range(offset, offset + layer_width):
            for z in range(offset, offset + layer_width):
                put(blocks, x, y, z, mat)


def angled_trunk(blocks: dict[tuple[int, int, int], str], mat: str, height: int, width: int, lean_x: int) -> tuple[int, int, int]:
    top = (0, 0, 0)
    for y in range(height):
        cx = round(lean_x * y / max(1, height - 1))
        layer_width = max(1, width)
        offset = -(layer_width // 2)
        for x in range(cx + offset, cx + offset + layer_width):
            for z in range(offset, offset + layer_width):
                put(blocks, x, y, z, mat)
        top = (cx, y, 0)
    return top


def branch(
    blocks: dict[tuple[int, int, int], str],
    mat: str,
    start: tuple[int, int, int],
    direction: tuple[int, int],
    length: int,
    thickness: int,
    rise: int = 1,
) -> tuple[int, int, int]:
    x, y, z = start
    dx, dz = direction
    tip = start
    for i in range(1, length + 1):
        bx = x + dx * i
        bz = z + dz * i
        by = y + round(rise * i / max(1, length))
        radius = max(0, thickness - 1)
        for ox in range(-radius, radius + 1):
            for oz in range(-radius, radius + 1):
                if abs(ox) + abs(oz) <= radius + 1:
                    put(blocks, bx + ox, by, bz + oz, mat)
        tip = (bx, by, bz)
    return tip


def ellipsoid(
    blocks: dict[tuple[int, int, int], str],
    mat: str,
    center: tuple[int, int, int],
    rx: int,
    ry: int,
    rz: int,
    *,
    density: float,
    seed: int,
) -> None:
    rnd = Random(seed)
    cx, cy, cz = center
    for x in range(cx - rx, cx + rx + 1):
        for y in range(cy - ry, cy + ry + 1):
            for z in range(cz - rz, cz + rz + 1):
                nx = (x - cx) / max(1, rx)
                ny = (y - cy) / max(1, ry)
                nz = (z - cz) / max(1, rz)
                score = nx * nx + ny * ny * 1.25 + nz * nz
                if score <= 1.0 and rnd.random() < density:
                    if (x, y, z) not in blocks:
                        put(blocks, x, y, z, mat)


def leaf_disc(blocks: dict[tuple[int, int, int], str], mat: str, y: int, radius: int, *, cx: int = 0, cz: int = 0, seed: int = 0) -> None:
    rnd = Random(seed)
    for x in range(cx - radius, cx + radius + 1):
        for z in range(cz - radius, cz + radius + 1):
            d = abs(x - cx) + abs(z - cz) * 0.85
            if d <= radius + 0.35 and rnd.random() > 0.08:
                if (x, y, z) not in blocks:
                    put(blocks, x, y, z, mat)


def fancy_blob(
    blocks: dict[tuple[int, int, int], str],
    mat: str,
    center: tuple[int, int, int],
    radius: int,
    *,
    layers: int = 3,
    seed: int = 0,
) -> None:
    """## Minecraft-like early tree crown: stacked uneven leaf discs, not a tiny ancient crown."""
    rnd = Random(seed)
    cx, cy, cz = center
    for layer in range(layers):
        y = cy + layer
        layer_radius = max(1, radius - max(0, layer - 1))
        for x in range(cx - layer_radius, cx + layer_radius + 1):
            for z in range(cz - layer_radius, cz + layer_radius + 1):
                edge = abs(x - cx) + abs(z - cz)
                if edge <= layer_radius + 1 and rnd.random() > (0.08 + layer * 0.04):
                    if (x, y, z) not in blocks:
                        put(blocks, x, y, z, mat)
    ellipsoid(blocks, mat, (cx, cy + layers, cz), max(1, radius - 1), 1, max(1, radius - 1), density=0.72, seed=seed + 77)


def add_ground(blocks: dict[tuple[int, int, int], str], radius: int) -> None:
    for x in range(-radius, radius + 1):
        for z in range(-radius, radius + 1):
            if abs(x) + abs(z) <= radius * 1.35:
                put(blocks, x, -1, z, "grass")


def add_vines(blocks: dict[tuple[int, int, int], str], around: list[tuple[int, int, int]], length: int, seed: int) -> None:
    rnd = Random(seed)
    for x, y, z in around:
        if rnd.random() > 0.45:
            continue
        for drop in range(1, length + rnd.randint(0, 2)):
            if (x, y - drop, z) not in blocks and y - drop > 1:
                put(blocks, x, y - drop, z, "vine")


def species_tree(spec: TreeSpec, stage: str) -> dict[tuple[int, int, int], str]:
    blocks: dict[tuple[int, int, int], str] = {}
    stage_index = STAGES.index(stage)
    seed = 1000 + stage_index * 31 + sum(ord(c) for c in spec.key)
    add_ground(blocks, 8 + stage_index * 2)

    if spec.key == "oak":
        heights = [5, 8, 13, 19]
        widths = [1, 2, 3, 4]
        h, w = heights[stage_index], widths[stage_index]
        square_trunk(blocks, spec.wood, w if stage_index >= 2 else 1, h, taper=True)
        if stage_index <= 1:
            fancy_blob(blocks, spec.leaf, (0, h - 1, 0), 3 + stage_index, layers=3 + stage_index, seed=seed)
            if stage_index == 1:
                for n, direction in enumerate([(1, 0), (-1, 0), (0, 1)]):
                    tip = branch(blocks, spec.wood, (0, h - 2, 0), direction, 1 + n % 2, 1, rise=0)
                    ellipsoid(blocks, spec.leaf, tip, 2, 1, 2, density=0.70, seed=seed + n)
        else:
            ellipsoid(blocks, spec.leaf, (0, h + 1, 0), 3 + stage_index, 2 + stage_index // 2, 3 + stage_index, density=0.82, seed=seed)
            for n, direction in enumerate([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)]):
                if n > stage_index + 1:
                    continue
                tip = branch(blocks, spec.wood, (0, h - 2 - n % 2, 0), direction, 2 + stage_index, 1 + stage_index // 2)
                ellipsoid(blocks, spec.leaf, tip, 2 + stage_index // 2, 1 + stage_index // 2, 2 + stage_index // 2, density=0.76, seed=seed + n)

    elif spec.key == "birch":
        heights = [7, 12, 19, 26]
        h = heights[stage_index]
        square_trunk(blocks, spec.wood, 1 if stage_index < 3 else 2, h)
        if stage_index <= 1:
            fancy_blob(blocks, spec.leaf, (0, h - 1, 0), 2 + stage_index, layers=3, seed=seed)
        else:
            ellipsoid(blocks, spec.leaf, (0, h + 1, 0), 2 + min(stage_index, 2), 2, 2 + min(stage_index, 2), density=0.78, seed=seed)
            for n, direction in enumerate([(1, 0), (-1, 0), (0, 1)]):
                tip = branch(blocks, spec.wood, (0, h - 4 - n, 0), direction, 2 + stage_index // 2, 1)
                ellipsoid(blocks, spec.leaf, tip, 2, 1, 2, density=0.68, seed=seed + n)

    elif spec.key == "spruce":
        heights = [8, 15, 27, 38]
        widths = [1, 1, 2, 2]
        h, w = heights[stage_index], widths[stage_index]
        square_trunk(blocks, spec.wood, w, h)
        shelf_count = [3, 5, 8, 11][stage_index]
        for i in range(shelf_count):
            y = 3 + i * max(2, h // (shelf_count + 1))
            radius = max(1, [3, 5, 7, 9][stage_index] - i // 2)
            leaf_disc(blocks, spec.leaf, y, radius, seed=seed + i)
            if i % 2 == 0 and y + 1 < h:
                leaf_disc(blocks, spec.leaf, y + 1, max(1, radius - 1), seed=seed + i + 50)
        ellipsoid(blocks, spec.leaf, (0, h, 0), 2, 2, 2, density=0.9, seed=seed + 99)

    elif spec.key == "jungle":
        heights = [9, 18, 34, 52]
        widths = [1, 2, 4, 5]
        h, w = heights[stage_index], widths[stage_index]
        square_trunk(blocks, spec.wood, w if stage_index >= 2 else 1, h, taper=True)
        tips = []
        if stage_index <= 1:
            fancy_blob(blocks, spec.leaf, (0, h, 0), 3 + stage_index, layers=3 + stage_index, seed=seed)
            add_vines(blocks, [(x, y, z) for (x, y, z), mat in blocks.items() if mat == spec.leaf and y > h - 2], 2 + stage_index, seed)
        else:
            ellipsoid(blocks, spec.leaf, (0, h + 2, 0), 3 + stage_index * 2, 2 + stage_index, 3 + stage_index * 2, density=0.78, seed=seed)
            dirs = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)]
            for n, direction in enumerate(dirs[: 2 + stage_index * 2]):
                tip = branch(blocks, spec.wood, (0, h - 5 - n % 3, 0), direction, 3 + stage_index * 2, 1 + stage_index // 2, rise=2)
                tips.append(tip)
                ellipsoid(blocks, spec.leaf, tip, 3 + stage_index, 2, 3 + stage_index, density=0.72, seed=seed + n)
            add_vines(blocks, [(x, y, z) for (x, y, z), mat in blocks.items() if mat == spec.leaf and y > h - 4], 4 + stage_index * 2, seed)

    elif spec.key == "dark_oak":
        heights = [5, 9, 15, 24]
        widths = [2, 3, 4, 5]
        h, w = heights[stage_index], widths[stage_index]
        square_trunk(blocks, spec.wood, w if stage_index >= 2 else 2, h, taper=True)
        if stage_index <= 1:
            fancy_blob(blocks, spec.leaf, (0, h - 1, 0), 4 + stage_index, layers=3 + stage_index, seed=seed)
        else:
            ellipsoid(blocks, spec.leaf, (0, h + 1, 0), 4 + stage_index * 2, 2 + stage_index, 4 + stage_index * 2, density=0.86, seed=seed)
            for n, direction in enumerate([(1, 0), (-1, 0), (0, 1), (0, -1)]):
                tip = branch(blocks, spec.wood, (0, h - 2 - n % 2, 0), direction, 3 + stage_index, 1 + stage_index // 2, rise=0)
                ellipsoid(blocks, spec.leaf, tip, 3 + stage_index, 2, 3 + stage_index, density=0.80, seed=seed + n)

    elif spec.key == "acacia":
        heights = [5, 9, 14, 20]
        lean = [1, 2, 4, 6][stage_index]
        top = angled_trunk(blocks, spec.wood, heights[stage_index], 1 if stage_index < 3 else 2, lean)
        if stage_index == 0:
            fancy_blob(blocks, spec.leaf, (top[0], top[1], top[2]), 3, layers=2, seed=seed)
            return blocks
        dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        for n, direction in enumerate(dirs[: 2 + min(stage_index, 2)]):
            tip = branch(blocks, spec.wood, top, direction, 2 + stage_index * 2, 1, rise=1)
            if stage_index == 1:
                fancy_blob(blocks, spec.leaf, tip, 2, layers=2, seed=seed + n)
            else:
                leaf_disc(blocks, spec.leaf, tip[1] + 1, 2 + stage_index * 2, cx=tip[0], cz=tip[2], seed=seed + n)
                leaf_disc(blocks, spec.leaf, tip[1] + 2, max(1, 1 + stage_index), cx=tip[0], cz=tip[2], seed=seed + n + 40)

    elif spec.key == "cherry":
        heights = [6, 10, 17, 26]
        widths = [1, 1, 2, 3]
        h, w = heights[stage_index], widths[stage_index]
        square_trunk(blocks, spec.wood, w, h, taper=True)
        if stage_index <= 1:
            fancy_blob(blocks, spec.leaf, (0, h - 1, 0), 3 + stage_index, layers=3 + stage_index, seed=seed)
        else:
            layer_radius = [3, 5, 7, 9][stage_index]
            for layer in range(2 + stage_index):
                cy = h + layer * 2
                ellipsoid(blocks, spec.leaf, (0, cy, 0), max(2, layer_radius - layer), 1 + stage_index // 2, max(2, layer_radius - layer), density=0.76, seed=seed + layer)
            for n, direction in enumerate([(1, 0), (-1, 0), (0, 1), (0, -1)]):
                tip = branch(blocks, spec.wood, (0, h - 3 - n % 2, 0), direction, 2 + stage_index, 1, rise=1)
                ellipsoid(blocks, spec.leaf, tip, 2 + stage_index, 1, 2 + stage_index, density=0.68, seed=seed + n + 20)

    return blocks


def project(x: int, y: int, z: int, scale: int) -> tuple[float, float]:
    return ((x - z) * scale * 0.62, (x + z) * scale * 0.34 - y * scale * 0.72)


def draw_cube(draw: ImageDraw.ImageDraw, x: int, y: int, z: int, mat: str, ox: float, oy: float, scale: int) -> None:
    color = COLORS[mat]
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
    edge = shade(color, 0.55)
    draw.line(shift([p010, p110, p111, p011, p010]), fill=edge, width=1)
    draw.line(shift([p001, p011, p111, p101, p001]), fill=edge, width=1)
    draw.line(shift([p100, p101, p111, p110, p100]), fill=edge, width=1)


def camera_for(block_sets: list[dict[tuple[int, int, int], str]], width: int, height: int) -> tuple[int, float, float]:
    all_keys = [pos for blocks in block_sets for pos in blocks]
    xs, ys, zs = zip(*all_keys)
    scale = max(6, min(15, int(620 / max(20, (max(xs) - min(xs) + max(zs) - min(zs) + max(ys) - min(ys))))))
    projected = [project(x, y, z, scale) for x, y, z in all_keys]
    min_px = min(px for px, _ in projected)
    max_px = max(px for px, _ in projected)
    min_py = min(py for _, py in projected)
    max_py = max(py for _, py in projected)
    ox = width / 2 - (min_px + max_px) / 2
    oy = height - 54 - max_py
    if min_py + oy < 54:
        oy += 54 - (min_py + oy)
    return scale, ox, oy


def render_blocks(
    blocks: dict[tuple[int, int, int], str],
    title: str,
    note: str,
    path: Path,
    *,
    width: int = 760,
    height: int = 580,
    camera: tuple[int, float, float] | None = None,
) -> None:
    image = Image.new("RGB", (width, height), (240, 244, 247))
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype("arial.ttf", 18)
        small_font = ImageFont.truetype("arial.ttf", 12)
    except OSError:
        font = ImageFont.load_default()
        small_font = ImageFont.load_default()

    if camera is None:
        scale, ox, oy = camera_for([blocks], width, height)
    else:
        scale, ox, oy = camera

    draw.text((10, 8), title, fill=(33, 42, 48), font=font)
    draw.text((10, 32), note, fill=(71, 84, 93), font=small_font)
    for (x, y, z), mat in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][0] + item[0][2], item[0][0])):
        draw_cube(draw, x, y, z, mat, ox, oy, scale)
    image.save(path)


def write_obj(block_sets: list[tuple[str, dict[tuple[int, int, int], str], tuple[int, int, int]]], path: Path) -> None:
    mtl_path = path.with_suffix(".mtl")
    material_names = sorted(COLORS)
    with mtl_path.open("w", encoding="utf-8") as mtl:
        mtl.write("## Evolution staged concept material palette\n")
        for name in material_names:
            r, g, b = COLORS[name]
            mtl.write(f"newmtl {name}\nKd {r / 255:.4f} {g / 255:.4f} {b / 255:.4f}\nKa 0.1 0.1 0.1\n\n")

    vertices: list[tuple[float, float, float]] = []
    faces: list[tuple[str, tuple[int, int, int, int]]] = []
    face_defs = [
        ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)),
        ((0, 0, 1), (0, 1, 1), (1, 1, 1), (1, 0, 1)),
        ((0, 0, 0), (0, 0, 1), (1, 0, 1), (1, 0, 0)),
        ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
        ((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)),
        ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)),
    ]
    for object_name, blocks, offset in block_sets:
        ox, oy, oz = offset
        for (x, y, z), mat in blocks.items():
            base = len(vertices) + 1
            for vx, vy, vz in [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]:
                vertices.append((x + ox + vx, y + oy + vy, z + oz + vz))
            index_map = {
                (0, 0, 0): base,
                (1, 0, 0): base + 1,
                (1, 1, 0): base + 2,
                (0, 1, 0): base + 3,
                (0, 0, 1): base + 4,
                (1, 0, 1): base + 5,
                (1, 1, 1): base + 6,
                (0, 1, 1): base + 7,
            }
            for face in face_defs:
                faces.append((mat, tuple(index_map[p] for p in face)))

    with path.open("w", encoding="utf-8") as obj:
        obj.write("## Evolution staged tree concept OBJ. Original procedural reference shapes.\n")
        obj.write(f"mtllib {mtl_path.name}\n")
        for v in vertices:
            obj.write(f"v {v[0]} {v[1]} {v[2]}\n")
        last_mat = None
        for mat, face in faces:
            if mat != last_mat:
                obj.write(f"usemtl {mat}\n")
                last_mat = mat
            obj.write("f " + " ".join(str(i) for i in face) + "\n")


def make_gallery(image_paths: list[Path], titles: list[str], path: Path, *, cols: int = 4) -> None:
    thumb_w, thumb_h = 520, 390
    rows = math.ceil(len(image_paths) / cols)
    gallery = Image.new("RGB", (cols * thumb_w, rows * thumb_h), (236, 240, 243))
    draw = ImageDraw.Draw(gallery)
    try:
        font = ImageFont.truetype("arial.ttf", 18)
    except OSError:
        font = ImageFont.load_default()
    for i, image_path in enumerate(image_paths):
        image = Image.open(image_path).resize((thumb_w, thumb_h - 28), Image.Resampling.LANCZOS)
        x = (i % cols) * thumb_w
        y = (i // cols) * thumb_h
        gallery.paste(image, (x, y))
        draw.text((x + 12, y + thumb_h - 25), titles[i], fill=(27, 35, 40), font=font)
    gallery.save(path)


def make_fixed_species_ladder(spec: TreeSpec, staged_blocks: list[tuple[str, dict[tuple[int, int, int], str]]], path: Path) -> None:
    panel_w, panel_h = 560, 430
    image = Image.new("RGB", (panel_w * 4, panel_h), (236, 240, 243))
    try:
        font = ImageFont.truetype("arial.ttf", 18)
        small_font = ImageFont.truetype("arial.ttf", 11)
    except OSError:
        font = ImageFont.load_default()
        small_font = ImageFont.load_default()
    camera = camera_for([blocks for _, blocks in staged_blocks], panel_w, panel_h)
    for index, (stage, blocks) in enumerate(staged_blocks):
        panel = PNG_OUT / f".tmp_{spec.key}_{stage}_fixed.png"
        render_blocks(
            blocks,
            f"{spec.title} - {stage.upper()}",
            f"## fixed-scale ladder view; {spec.note}",
            panel,
            width=panel_w,
            height=panel_h,
            camera=camera,
        )
        tile = Image.open(panel)
        image.paste(tile, (index * panel_w, 0))
        panel.unlink(missing_ok=True)
    draw = ImageDraw.Draw(image)
    draw.text((12, panel_h - 24), f"{spec.title} fixed-scale stage ladder", fill=(27, 35, 40), font=font)
    draw.text((12, panel_h - 42), "## same camera across SMALL/MEDIUM/MATURE/ANCIENT, so size changes are visible", fill=(71, 84, 93), font=small_font)
    image.save(path)


def main() -> None:
    PNG_OUT.mkdir(parents=True, exist_ok=True)
    OBJ_OUT.mkdir(parents=True, exist_ok=True)
    all_for_obj: list[tuple[str, dict[tuple[int, int, int], str], tuple[int, int, int]]] = []
    all_pngs: list[Path] = []
    all_titles: list[str] = []

    for spec_index, spec in enumerate(SPECS):
        species_pngs: list[Path] = []
        species_titles: list[str] = []
        staged_blocks: list[tuple[str, dict[tuple[int, int, int], str]]] = []
        for stage_index, stage in enumerate(STAGES):
            blocks = species_tree(spec, stage)
            staged_blocks.append((stage, blocks))
            title = f"{spec.title} - {stage.upper()}"
            note = f"## {spec.note}; staged concept reference, not copied assets."
            png_path = PNG_OUT / f"{spec.key}_{stage}.png"
            render_blocks(blocks, title, note, png_path)
            write_obj([(f"{spec.key}_{stage}", blocks, (0, 0, 0))], OBJ_OUT / f"{spec.key}_{stage}.obj")
            species_pngs.append(png_path)
            species_titles.append(stage)
            all_pngs.append(png_path)
            all_titles.append(f"{spec.title} {stage}")
            all_for_obj.append((f"{spec.key}_{stage}", blocks, (stage_index * 34, 0, spec_index * 34)))
        make_gallery(species_pngs, [f"{spec.title} {s}" for s in species_titles], PNG_OUT / f"{spec.key}_stage_ladder.png", cols=4)
        make_fixed_species_ladder(spec, staged_blocks, PNG_OUT / f"{spec.key}_stage_ladder_fixed_scale.png")

    make_gallery(all_pngs, all_titles, PNG_OUT / "all_species_stage_gallery.png", cols=4)
    write_obj(all_for_obj, OBJ_OUT / "all_species_stage_models.obj")

    readme = OUT / "README.md"
    readme.write_text(
        "## Evolution staged tree models\n"
        "These PNG and OBJ files show SMALL, MEDIUM, MATURE, and ANCIENT concept shapes for each species.\n"
        "They are original procedural reference models for tuning TreeEvolutionFeature and TreeSpeciesStageStyle.\n\n"
        "- png/all_species_stage_gallery.png: full visual growth ladder.\n"
        "- png/<species>_stage_ladder.png: one species across all stages.\n"
        "- png/<species>_stage_ladder_fixed_scale.png: one species across all stages using the same camera scale.\n"
        "- obj/all_species_stage_models.obj: combined 3D model lineup.\n"
        "- obj/<species>_<stage>.obj: individual stage model.\n\n"
        "## Notes\n"
        "SMALL/MEDIUM/MATURE are not just scaled-down ancient trees. Each species gets its own growth story:\n"
        "birch stays slim, spruce stacks shelves, jungle becomes a landmark, acacia forks outward, and dark oak/cherry thicken in their own style.\n",
        encoding="utf-8",
    )
    print(PNG_OUT / "all_species_stage_gallery.png")
    print(OBJ_OUT / "all_species_stage_models.obj")


if __name__ == "__main__":
    main()
