#!/usr/bin/env python3
"""Restore Character 1's approved facial read before rigging.

The multi-view reconstruction preserved the expedition outfit but collapsed the approved
black glasses into dark round sockets. This pass keeps the generated head and textures,
covers only the failed socket regions with face-matched patches, authors small natural
eyes and brows, and adds a real shallow three-dimensional pair of rounded rectangular
black glasses with bridge and temple arms. All authored geometry is exported with the
source mesh and is large enough to survive the production capture and Unity FBX path.
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys
import traceback

import bpy
from mathutils import Vector


def cli_args() -> list[str]:
    return sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(cli_args())


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)


def import_glb(path: pathlib.Path):
    bpy.ops.import_scene.gltf(filepath=str(path))
    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError(f"No mesh objects imported from {path}")
    return meshes


def world_bounds(meshes):
    points = [obj.matrix_world @ Vector(corner) for obj in meshes for corner in obj.bound_box]
    if not points:
        raise RuntimeError("No world-space mesh bounds were available")
    minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
    maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
    return minimum, maximum


def world_vertices(meshes):
    for obj in meshes:
        matrix = obj.matrix_world
        for vertex in obj.data.vertices:
            yield matrix @ vertex.co


def quantile(values, fraction: float) -> float:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise RuntimeError("Cannot compute a quantile from an empty collection")
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def material(name: str, rgba, roughness: float, specular: float):
    value = bpy.data.materials.new(name)
    value.use_nodes = True
    value.diffuse_color = rgba
    principled = value.node_tree.nodes.get("Principled BSDF")
    if principled:
        if "Base Color" in principled.inputs:
            principled.inputs["Base Color"].default_value = rgba
        if "Roughness" in principled.inputs:
            principled.inputs["Roughness"].default_value = roughness
        if "Metallic" in principled.inputs:
            principled.inputs["Metallic"].default_value = 0.0
        if "Specular IOR Level" in principled.inputs:
            principled.inputs["Specular IOR Level"].default_value = specular
        elif "Specular" in principled.inputs:
            principled.inputs["Specular"].default_value = specular
    return value


def oval_points(radius_x: float, radius_z: float, segments: int):
    return [
        (
            math.cos(math.tau * index / segments) * radius_x,
            math.sin(math.tau * index / segments) * radius_z,
        )
        for index in range(segments)
    ]


def almond_points(radius_x: float, radius_z: float, half_segments: int):
    points = []
    for index in range(half_segments + 1):
        u = index / half_segments
        points.append((-radius_x + 2.0 * radius_x * u, radius_z * math.sin(math.pi * u)))
    for index in range(1, half_segments):
        u = 1.0 - index / half_segments
        points.append((-radius_x + 2.0 * radius_x * u, -radius_z * math.sin(math.pi * u)))
    return points


def superellipse_points(half_width: float, half_height: float, exponent: float, segments: int):
    points = []
    power = 2.0 / exponent
    for index in range(segments):
        angle = math.tau * index / segments
        cosine = math.cos(angle)
        sine = math.sin(angle)
        x = math.copysign(abs(cosine) ** power, cosine) * half_width
        z = math.copysign(abs(sine) ** power, sine) * half_height
        points.append((x, z))
    return points


def flat_shape(name: str, location, perimeter, assigned_material, layer: str):
    if len(perimeter) < 100:
        raise RuntimeError(f"{name} requires at least 100 perimeter vertices")
    x, y, z = location
    vertices = [(x, y, z)] + [(x + px, y, z + pz) for px, pz in perimeter]
    faces = [
        (0, 1 + index, 1 + ((index + 1) % len(perimeter)))
        for index in range(len(perimeter))
    ]
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.update(calc_edges=True)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(assigned_material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = False
    obj["havenlineCharacter1FaceDetail"] = True
    obj["havenlineFaceLayer"] = layer
    obj["havenlineProductionCaptureEligible"] = len(mesh.vertices) >= 101
    return obj


def append_prism(vertices, faces, x0, x1, y0, y1, z0, z1):
    base = len(vertices)
    vertices.extend(
        [
            (x0, y0, z0),
            (x1, y0, z0),
            (x1, y1, z0),
            (x0, y1, z0),
            (x0, y0, z1),
            (x1, y0, z1),
            (x1, y1, z1),
            (x0, y1, z1),
        ]
    )
    faces.extend(
        [
            (base + 0, base + 1, base + 2, base + 3),
            (base + 4, base + 7, base + 6, base + 5),
            (base + 0, base + 4, base + 5, base + 1),
            (base + 1, base + 5, base + 6, base + 2),
            (base + 2, base + 6, base + 7, base + 3),
            (base + 3, base + 7, base + 4, base + 0),
        ]
    )


def append_bar_xy(vertices, faces, start, end, half_width: float, half_height: float):
    x0, y0, z = start
    x1, y1, _ = end
    dx = x1 - x0
    dy = y1 - y0
    length = math.hypot(dx, dy)
    if length <= 1e-8:
        raise RuntimeError("Cannot author a zero-length glasses temple")
    px = -dy / length * half_width
    py = dx / length * half_width
    base = len(vertices)
    lower = z - half_height
    upper = z + half_height
    vertices.extend(
        [
            (x0 + px, y0 + py, lower),
            (x0 - px, y0 - py, lower),
            (x1 - px, y1 - py, lower),
            (x1 + px, y1 + py, lower),
            (x0 + px, y0 + py, upper),
            (x0 - px, y0 - py, upper),
            (x1 - px, y1 - py, upper),
            (x1 + px, y1 + py, upper),
        ]
    )
    faces.extend(
        [
            (base + 0, base + 1, base + 2, base + 3),
            (base + 4, base + 7, base + 6, base + 5),
            (base + 0, base + 4, base + 5, base + 1),
            (base + 1, base + 5, base + 6, base + 2),
            (base + 2, base + 6, base + 7, base + 3),
            (base + 3, base + 7, base + 4, base + 0),
        ]
    )


def append_extruded_ring(vertices, faces, center_x, center_z, front_y, back_y, outer, inner):
    if len(outer) != len(inner):
        raise RuntimeError("Glasses ring loops must contain the same number of points")
    count = len(outer)
    base = len(vertices)
    vertices.extend((center_x + x, front_y, center_z + z) for x, z in outer)
    vertices.extend((center_x + x, front_y, center_z + z) for x, z in inner)
    vertices.extend((center_x + x, back_y, center_z + z) for x, z in outer)
    vertices.extend((center_x + x, back_y, center_z + z) for x, z in inner)
    outer_front = base
    inner_front = base + count
    outer_back = base + count * 2
    inner_back = base + count * 3
    for index in range(count):
        following = (index + 1) % count
        faces.extend(
            [
                (
                    outer_front + index,
                    outer_front + following,
                    inner_front + following,
                    inner_front + index,
                ),
                (
                    outer_back + index,
                    inner_back + index,
                    inner_back + following,
                    outer_back + following,
                ),
                (
                    outer_front + index,
                    outer_back + index,
                    outer_back + following,
                    outer_front + following,
                ),
                (
                    inner_front + index,
                    inner_front + following,
                    inner_back + following,
                    inner_back + index,
                ),
            ]
        )


def glasses_mesh(character: str, frame, surfaces, assigned_material):
    height = frame["height"]
    eye_z = frame["eyeZ"] + height * 0.0010
    front_y = min(surfaces) - height * 0.0048
    back_y = front_y + height * 0.00145
    half_width = height * 0.0208
    half_height = height * 0.0106
    frame_width = height * 0.00175
    outer = superellipse_points(half_width, half_height, 4.4, 128)
    inner = superellipse_points(
        half_width - frame_width,
        half_height - frame_width,
        4.4,
        128,
    )
    left_x = frame["centerX"] - frame["eyeOffsetX"]
    right_x = frame["centerX"] + frame["eyeOffsetX"]
    vertices = []
    faces = []
    append_extruded_ring(vertices, faces, left_x, eye_z, front_y, back_y, outer, inner)
    append_extruded_ring(vertices, faces, right_x, eye_z, front_y, back_y, outer, inner)

    bridge_left = left_x + half_width - frame_width * 0.35
    bridge_right = right_x - half_width + frame_width * 0.35
    if bridge_right <= bridge_left:
        midpoint = (left_x + right_x) * 0.5
        bridge_left = midpoint - height * 0.0030
        bridge_right = midpoint + height * 0.0030
    append_prism(
        vertices,
        faces,
        bridge_left,
        bridge_right,
        front_y,
        back_y,
        eye_z - height * 0.00125,
        eye_z + height * 0.00125,
    )

    temple_z = eye_z + height * 0.0018
    for side, center_x in ((-1, left_x), (1, right_x)):
        start_x = center_x + side * (half_width - frame_width * 0.15)
        start_y = (front_y + back_y) * 0.5
        end_x = start_x + side * height * 0.0160
        end_y = back_y + height * 0.0120
        append_bar_xy(
            vertices,
            faces,
            (start_x, start_y, temple_z),
            (end_x, end_y, temple_z),
            height * 0.00115,
            height * 0.00120,
        )

    mesh = bpy.data.meshes.new(f"{character}_ApprovedGlassesMesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.update(calc_edges=True)
    obj = bpy.data.objects.new(f"{character}_ApprovedGlasses", mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(assigned_material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = False
    obj["havenlineCharacter1FaceDetail"] = True
    obj["havenlineFaceLayer"] = "three-dimensional rounded rectangular glasses"
    obj["havenlineProductionCaptureEligible"] = len(mesh.vertices) >= 101
    if len(mesh.vertices) < 101:
        raise RuntimeError("Character 1 glasses would be omitted by production capture")
    return obj, {
        "frontY": front_y,
        "backY": back_y,
        "leftCenterX": left_x,
        "rightCenterX": right_x,
        "centerZ": eye_z,
        "vertices": len(mesh.vertices),
        "polygons": len(mesh.polygons),
    }


def eye_frame(meshes):
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    height = max(extent.z, 1e-6)
    width = max(extent.x, 1e-6)
    center_x = (minimum.x + maximum.x) * 0.5
    samples = [
        point
        for point in world_vertices(meshes)
        if minimum.z + height * 0.77 <= point.z <= minimum.z + height * 0.94
        and abs(point.x - center_x) <= width * 0.20
    ]
    if len(samples) < 40:
        raise RuntimeError(f"Not enough Character 1 facial samples: {len(samples)}")
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": center_x + height * 0.0040,
        "eyeZ": minimum.z + height * 0.8540,
        "eyeOffsetX": min(height * 0.0310, width * 0.074),
        "sampleCount": len(samples),
    }


def local_front_y(meshes, x: float, z: float, height: float):
    samples = []
    for point in world_vertices(meshes):
        dx = (point.x - x) / max(height * 0.022, 1e-6)
        dz = (point.z - z) / max(height * 0.022, 1e-6)
        if dx * dx + dz * dz <= 1.0:
            samples.append(point.y)
    if len(samples) < 20:
        raise RuntimeError(f"Not enough local Character 1 socket samples at x={x:.6f}, z={z:.6f}")
    return quantile(samples, 0.012), len(samples)


def author_face(character: str, frame, meshes):
    height = frame["height"]
    skin = material(f"{character}_FacePatch", (0.047, 0.026, 0.014, 1.0), 0.76, 0.040)
    lid = material(f"{character}_EyeLid", (0.0048, 0.0010, 0.00045, 1.0), 0.91, 0.014)
    sclera = material(f"{character}_WarmSclera", (0.20, 0.155, 0.105, 1.0), 0.84, 0.025)
    iris = material(f"{character}_DarkBrownIris", (0.0075, 0.0017, 0.00055, 1.0), 0.91, 0.012)
    pupil = material(f"{character}_Pupil", (0.00008, 0.00008, 0.00010, 1.0), 0.95, 0.008)
    frame_material = material(f"{character}_BlackGlasses", (0.0012, 0.0012, 0.0015, 1.0), 0.48, 0.18)
    brow_material = material(f"{character}_Brows", (0.0035, 0.0008, 0.00035, 1.0), 0.90, 0.012)

    authored = []
    placements = []
    surfaces = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        z = frame["eyeZ"]
        surface, count = local_front_y(meshes, x, z, height)
        surfaces.append(surface)
        patch_y = surface - height * 0.0018
        lid_y = patch_y - height * 0.00038
        sclera_y = lid_y - height * 0.00026
        iris_y = sclera_y - height * 0.00023
        pupil_y = iris_y - height * 0.00019
        brow_y = patch_y - height * 0.00034
        eye_z = z - height * 0.0006
        brow_z = z + height * 0.0105

        authored.extend(
            [
                flat_shape(
                    f"{character}_SocketPatch_{side}",
                    (x, patch_y, z),
                    oval_points(height * 0.0148, height * 0.0108, 128),
                    skin,
                    "face-coloured socket patch",
                ),
                flat_shape(
                    f"{character}_EyeLid_{side}",
                    (x, lid_y, eye_z),
                    almond_points(height * 0.0070, height * 0.00275, 64),
                    lid,
                    "small dark almond lid",
                ),
                flat_shape(
                    f"{character}_Sclera_{side}",
                    (x, sclera_y, eye_z),
                    almond_points(height * 0.0058, height * 0.00190, 64),
                    sclera,
                    "restrained warm sclera",
                ),
                flat_shape(
                    f"{character}_Iris_{side}",
                    (x, iris_y, eye_z),
                    oval_points(height * 0.00245, height * 0.00225, 112),
                    iris,
                    "small dark-brown iris",
                ),
                flat_shape(
                    f"{character}_Pupil_{side}",
                    (x, pupil_y, eye_z),
                    oval_points(height * 0.00108, height * 0.00125, 104),
                    pupil,
                    "small black pupil",
                ),
                flat_shape(
                    f"{character}_Brow_{side}",
                    (x + side * height * 0.0009, brow_y, brow_z),
                    almond_points(height * 0.0072, height * 0.00120, 64),
                    brow_material,
                    "approved dark eyebrow",
                ),
            ]
        )
        placements.append(
            {
                "side": side,
                "x": x,
                "z": z,
                "localFrontY": surface,
                "patchY": patch_y,
                "lidY": lid_y,
                "scleraY": sclera_y,
                "irisY": iris_y,
                "pupilY": pupil_y,
                "localSampleCount": count,
            }
        )

    glasses, glasses_report = glasses_mesh(character, frame, surfaces, frame_material)
    authored.append(glasses)
    if any(len(obj.data.vertices) < 101 for obj in authored):
        raise RuntimeError("An authored Character 1 face object would be hidden by production capture")
    return authored, placements, glasses_report


def export_glb(path: pathlib.Path, meshes) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.export_scene.gltf(
        filepath=str(path),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
    )
    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError(f"No refined GLB was exported to {path}")


def main() -> int:
    args = parse_args()
    root = pathlib.Path(args.output)
    root.mkdir(parents=True, exist_ok=True)
    output = root / f"{args.character}_face_refined.glb"
    report_path = root / "character1-face-refinement-report.json"
    report = {
        "schemaVersion": 1,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "method": "face-matched socket repair, small natural eyes and modeled rounded rectangular glasses",
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = eye_frame(meshes)
        authored, placements, glasses_report = author_face(args.character, frame, meshes)
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        export_glb(output, all_meshes)
        report.update(
            success=True,
            eyeFrame={
                "centerX": frame["centerX"],
                "eyeZ": frame["eyeZ"],
                "eyeOffsetX": frame["eyeOffsetX"],
                "sampleCount": frame["sampleCount"],
            },
            eyePlacements=placements,
            glasses=glasses_report,
            modeledObjectsCreated=len(authored),
            modeledVertexCounts={obj.name: len(obj.data.vertices) for obj in authored},
            outputBytes=output.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
