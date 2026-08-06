#!/usr/bin/env python3
"""Rebuild Character 3's eyes as small approved-style almond eyes.

The TRELLIS mesh contains oversized circular eye sockets. Adding irises alone left a
large pale ring and an uncanny expression. This pass keeps the generated head, eyelids,
hair, body, clothing and textures, but masks each failed socket with a small matte skin
patch and authors a compact horizontal almond eye over it: warm sclera, dark-brown iris
and black pupil. Every authored layer exceeds the production renderer's 100-vertex mesh
threshold and is positioned from the measured local socket surface.
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


def args_after_separator() -> list[str]:
    values = sys.argv
    return values[values.index("--") + 1 :] if "--" in values else []


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args(args_after_separator())


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


def quantile(values, fraction: float):
    ordered = sorted(float(value) for value in values)
    if not ordered:
        raise RuntimeError("Cannot compute a quantile from an empty collection")
    index = max(0, min(len(ordered) - 1, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]


def make_material(name: str, rgba, roughness: float, specular: float = 0.10):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    material.diffuse_color = rgba
    principled = material.node_tree.nodes.get("Principled BSDF")
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
    return material


def apply_material(obj, material) -> None:
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.material_index = 0
        polygon.use_smooth = False


def oval_points(radius_x: float, radius_z: float, segments: int):
    for index in range(segments):
        angle = math.tau * index / segments
        yield math.cos(angle) * radius_x, math.sin(angle) * radius_z


def almond_points(radius_x: float, radius_z: float, half_segments: int):
    # Two sine arcs meet at pointed inner and outer corners, producing a horizontal eye.
    for index in range(half_segments + 1):
        u = index / half_segments
        yield -radius_x + 2.0 * radius_x * u, radius_z * math.sin(math.pi * u)
    for index in range(1, half_segments):
        u = 1.0 - index / half_segments
        yield -radius_x + 2.0 * radius_x * u, -radius_z * math.sin(math.pi * u)


def flat_shape(
    name: str,
    location,
    points,
    material,
    layer: str,
):
    center_x, center_y, center_z = location
    perimeter = list(points)
    if len(perimeter) < 100:
        raise RuntimeError(f"{name} requires at least 100 perimeter vertices")
    vertices = [(center_x, center_y, center_z)] + [
        (center_x + x, center_y, center_z + z) for x, z in perimeter
    ]
    faces = []
    for index in range(len(perimeter)):
        current = 1 + index
        following = 1 + ((index + 1) % len(perimeter))
        # Front proof camera is on negative Y, so this winding faces -Y.
        faces.append((0, current, following))
    mesh = bpy.data.meshes.new(name + "Mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.update(calc_edges=True)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    apply_material(obj, material)
    obj["havenlineModeledEyeDetail"] = True
    obj["havenlineEyeLayer"] = layer
    obj["havenlineProductionCaptureEligible"] = len(mesh.vertices) >= 101
    return obj


def estimate_eye_frame(meshes):
    minimum, maximum = world_bounds(meshes)
    extent = maximum - minimum
    height = max(extent.z, 1e-6)
    width = max(extent.x, 1e-6)
    bounds_center_x = (minimum.x + maximum.x) * 0.5
    upper = [
        point
        for point in world_vertices(meshes)
        if minimum.z + height * 0.77 <= point.z <= minimum.z + height * 0.94
        and abs(point.x - bounds_center_x) <= width * 0.19
    ]
    if len(upper) < 40:
        raise RuntimeError(f"Not enough facial samples to place eyes safely: {len(upper)}")
    return {
        "minimum": minimum,
        "maximum": maximum,
        "height": height,
        "width": width,
        "centerX": bounds_center_x + height * 0.0080,
        "eyeZ": minimum.z + height * 0.854,
        "eyeOffsetX": min(height * 0.0305, width * 0.077),
        "sampleCount": len(upper),
    }


def local_eye_surface(meshes, eye_x: float, eye_z: float, height: float):
    radius_x = height * 0.020
    radius_z = height * 0.022
    samples = []
    for point in world_vertices(meshes):
        dx = (point.x - eye_x) / max(radius_x, 1e-6)
        dz = (point.z - eye_z) / max(radius_z, 1e-6)
        if dx * dx + dz * dz <= 1.0:
            samples.append(point.y)
    if len(samples) < 20:
        raise RuntimeError(
            f"Not enough local socket samples at x={eye_x:.6f}, z={eye_z:.6f}: {len(samples)}"
        )
    # Negative Y faces the front camera. Use the local foremost percentile.
    return quantile(samples, 0.012), len(samples)


def add_eyes(character: str, frame, meshes):
    height = frame["height"]
    # Approximate local face color in linear space, sampled from the reviewed proof.
    skin = make_material(f"{character}_EyeSocketSkin", (0.19, 0.052, 0.011, 1.0), 0.72, 0.08)
    sclera = make_material(f"{character}_WarmSclera", (0.48, 0.39, 0.29, 1.0), 0.70, 0.09)
    iris = make_material(f"{character}_DarkBrownIris", (0.012, 0.0025, 0.0008, 1.0), 0.82, 0.04)
    pupil = make_material(f"{character}_Pupil", (0.00015, 0.00015, 0.0002, 1.0), 0.90, 0.02)

    created = []
    placements = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        z = frame["eyeZ"] - height * 0.0010
        surface_y, local_samples = local_eye_surface(meshes, x, z, height)

        # Layer toward the front camera (negative Y): mask, sclera, iris, pupil.
        mask_y = surface_y - height * 0.0020
        sclera_y = mask_y - height * 0.00045
        iris_y = sclera_y - height * 0.00035
        pupil_y = iris_y - height * 0.00030

        created.append(
            flat_shape(
                f"{character}_SocketMask_{side}",
                (x, mask_y, z),
                oval_points(height * 0.0157, height * 0.0142, 128),
                skin,
                "skin socket mask",
            )
        )
        created.append(
            flat_shape(
                f"{character}_Sclera_{side}",
                (x, sclera_y, z - height * 0.0003),
                almond_points(height * 0.0112, height * 0.0048, 64),
                sclera,
                "horizontal almond sclera",
            )
        )
        created.append(
            flat_shape(
                f"{character}_Iris_{side}",
                (x, iris_y, z - height * 0.0003),
                oval_points(height * 0.0044, height * 0.0045, 112),
                iris,
                "dark brown iris",
            )
        )
        created.append(
            flat_shape(
                f"{character}_Pupil_{side}",
                (x, pupil_y, z - height * 0.0003),
                oval_points(height * 0.0021, height * 0.0024, 104),
                pupil,
                "black pupil",
            )
        )
        placements.append(
            {
                "side": side,
                "x": x,
                "z": z,
                "localFrontY": surface_y,
                "maskY": mask_y,
                "scleraY": sclera_y,
                "irisY": iris_y,
                "pupilY": pupil_y,
                "localSampleCount": local_samples,
            }
        )

    for obj in created:
        if len(obj.data.vertices) < 101:
            raise RuntimeError(
                f"Production renderer would hide {obj.name}: only {len(obj.data.vertices)} vertices"
            )
    return created, placements


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
    output_root = pathlib.Path(args.output)
    output_root.mkdir(parents=True, exist_ok=True)
    output_path = output_root / f"{args.character}_face_refined.glb"
    report_path = output_root / "multiview-eye-refinement-report.json"
    report = {
        "schemaVersion": 8,
        "character": args.character,
        "source": args.input,
        "output": str(output_path),
        "success": False,
        "method": "skin socket masks plus compact horizontal almond sclera, dark iris and pupil layers",
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = estimate_eye_frame(meshes)
        created, placements = add_eyes(args.character, frame, meshes)
        all_meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
        export_glb(output_path, all_meshes)
        report.update(
            success=True,
            eyeFrame={
                "centerX": frame["centerX"],
                "eyeZ": frame["eyeZ"],
                "eyeOffsetX": frame["eyeOffsetX"],
                "sampleCount": frame["sampleCount"],
            },
            eyePlacements=placements,
            modeledObjectsCreated=len(created),
            modeledEyeVertexCounts={obj.name: len(obj.data.vertices) for obj in created},
            allModeledEyesProductionCaptureEligible=all(
                len(obj.data.vertices) >= 101 for obj in created
            ),
            outputBytes=output_path.stat().st_size,
        )
    except Exception as exc:
        report.update(error=repr(exc), traceback=traceback.format_exc())
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
