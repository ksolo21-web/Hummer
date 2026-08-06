#!/usr/bin/env python3
"""Reshape Character 3's failed round sockets into compact expedition-character eyes.

The generated face, hair, body and clothing remain intact. Each oversized pale socket is
covered by a dark matte eyelid-shaped mask, then rebuilt as a narrow warm almond eye with
a large dark-brown iris and small black pupil. The layers are flat, non-billboard geometry,
measured from each socket's local front surface and large enough to survive production
capture, rigging and Unity FBX export.
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


def shape(name: str, location, perimeter, assigned_material, layer: str):
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
    obj["havenlineModeledEyeDetail"] = True
    obj["havenlineEyeLayer"] = layer
    obj["havenlineProductionCaptureEligible"] = len(mesh.vertices) >= 101
    return obj


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
        and abs(point.x - center_x) <= width * 0.19
    ]
    if len(samples) < 40:
        raise RuntimeError(f"Not enough facial samples: {len(samples)}")
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "centerX": center_x + height * 0.0080,
        "eyeZ": minimum.z + height * 0.853,
        "eyeOffsetX": min(height * 0.0305, width * 0.077),
        "sampleCount": len(samples),
    }


def local_front_y(meshes, x: float, z: float, height: float):
    samples = []
    for point in world_vertices(meshes):
        dx = (point.x - x) / max(height * 0.020, 1e-6)
        dz = (point.z - z) / max(height * 0.021, 1e-6)
        if dx * dx + dz * dz <= 1.0:
            samples.append(point.y)
    if len(samples) < 20:
        raise RuntimeError(f"Not enough local socket samples at x={x:.6f}, z={z:.6f}")
    return quantile(samples, 0.012), len(samples)


def author_eyes(character: str, frame, meshes):
    height = frame["height"]
    # Dark mask reads as the character's lashes/eyelids and avoids the orange pasted-on
    # skin patches produced by the prior material approximation.
    socket = material(f"{character}_DarkSocket", (0.014, 0.0028, 0.0008, 1.0), 0.86, 0.025)
    sclera = material(f"{character}_WarmSclera", (0.13, 0.085, 0.050, 1.0), 0.82, 0.035)
    iris = material(f"{character}_DarkIris", (0.0060, 0.0013, 0.00045, 1.0), 0.90, 0.015)
    pupil = material(f"{character}_Pupil", (0.00010, 0.00010, 0.00013, 1.0), 0.94, 0.010)

    authored = []
    placements = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        z = frame["eyeZ"]
        surface, count = local_front_y(meshes, x, z, height)
        mask_y = surface - height * 0.0020
        sclera_y = mask_y - height * 0.00042
        iris_y = sclera_y - height * 0.00032
        pupil_y = iris_y - height * 0.00027

        authored.extend(
            [
                shape(
                    f"{character}_SocketMask_{side}",
                    (x, mask_y, z),
                    oval_points(height * 0.0143, height * 0.0118, 128),
                    socket,
                    "dark eyelid socket mask",
                ),
                shape(
                    f"{character}_Sclera_{side}",
                    (x, sclera_y, z - height * 0.0006),
                    almond_points(height * 0.0088, height * 0.00335, 64),
                    sclera,
                    "narrow warm almond sclera",
                ),
                shape(
                    f"{character}_Iris_{side}",
                    (x, iris_y, z - height * 0.0006),
                    oval_points(height * 0.00355, height * 0.00320, 112),
                    iris,
                    "large dark-brown iris",
                ),
                shape(
                    f"{character}_Pupil_{side}",
                    (x, pupil_y, z - height * 0.0006),
                    oval_points(height * 0.00175, height * 0.00190, 104),
                    pupil,
                    "small black pupil",
                ),
            ]
        )
        placements.append(
            {
                "side": side,
                "x": x,
                "z": z,
                "localFrontY": surface,
                "maskY": mask_y,
                "scleraY": sclera_y,
                "irisY": iris_y,
                "pupilY": pupil_y,
                "localSampleCount": count,
            }
        )

    if any(len(obj.data.vertices) < 101 for obj in authored):
        raise RuntimeError("An authored eye layer would be hidden by production capture")
    return authored, placements


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
    report_path = root / "multiview-eye-refinement-report.json"
    report = {
        "schemaVersion": 9,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "method": "dark eyelid masks plus narrow warm almond eyes with large dark irises",
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = eye_frame(meshes)
        authored, placements = author_eyes(args.character, frame, meshes)
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
            modeledObjectsCreated=len(authored),
            modeledEyeVertexCounts={obj.name: len(obj.data.vertices) for obj in authored},
            outputBytes=output.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
