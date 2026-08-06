#!/usr/bin/env python3
"""Remove Character 3's pale reconstructed eye surfaces and inset dark almond eyes.

Flat skin-coloured patches remained visibly circular under production lighting. This pass
therefore does not paint over the failed sockets. It measures each approved eye centre,
deletes only the foremost vertices inside a small socket ellipse, preserves the deeper
head/eyelid geometry, and places compact dark-brown almond eyes slightly inside the
cleaned openings. No visible sclera or face patch is authored.
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
        "eyeZ": minimum.z + height * 0.8505,
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


def socket_indices(obj, eye_x: float, eye_z: float, front_y: float, height: float):
    radius_x = height * 0.0128
    radius_z = height * 0.0098
    maximum_y = front_y + height * 0.0038
    minimum_y = front_y - height * 0.0045
    matrix = obj.matrix_world
    selected = []
    for vertex in obj.data.vertices:
        point = matrix @ vertex.co
        dx = (point.x - eye_x) / max(radius_x, 1e-6)
        dz = (point.z - eye_z) / max(radius_z, 1e-6)
        if dx * dx + dz * dz <= 1.0 and minimum_y <= point.y <= maximum_y:
            selected.append(vertex.index)
    return selected


def delete_indices(obj, indices):
    if not indices:
        return 0
    if bpy.context.object and bpy.context.object.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_mode(type="VERT")
    bpy.ops.mesh.select_all(action="DESELECT")
    bpy.ops.object.mode_set(mode="OBJECT")
    for vertex in obj.data.vertices:
        vertex.select = False
    for index in indices:
        obj.data.vertices[index].select = True
    selected_count = sum(1 for vertex in obj.data.vertices if vertex.select)
    if selected_count != len(indices):
        raise RuntimeError(
            f"Socket selection synchronization failed for {obj.name}: "
            f"expected {len(indices)}, selected {selected_count}"
        )
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.delete(type="VERT")
    bpy.ops.object.mode_set(mode="OBJECT")
    obj.data.validate(verbose=False)
    obj.data.update(calc_edges=False, calc_edges_loose=False)
    return selected_count


def remove_failed_socket_surfaces(meshes, frame):
    height = frame["height"]
    reports = []
    total_before = sum(len(obj.data.vertices) for obj in meshes)
    total_removed = 0
    placements = []
    for side in (-1, 1):
        x = frame["centerX"] + side * frame["eyeOffsetX"]
        z = frame["eyeZ"]
        front_y, sample_count = local_front_y(meshes, x, z, height)
        removed_for_eye = 0
        object_reports = []
        for obj in meshes:
            before = len(obj.data.vertices)
            indices = socket_indices(obj, x, z, front_y, height)
            if indices and len(indices) / max(before, 1) > 0.08:
                raise RuntimeError(
                    f"Unsafe socket selection for {obj.name}, side {side}: "
                    f"{len(indices)}/{before}"
                )
            removed = delete_indices(obj, indices)
            after = len(obj.data.vertices)
            if removed != before - after:
                raise RuntimeError(
                    f"Socket deletion count mismatch for {obj.name}: selected={removed}, "
                    f"actual={before-after}"
                )
            if removed:
                object_reports.append(
                    {
                        "object": obj.name,
                        "verticesBefore": before,
                        "verticesAfter": after,
                        "verticesRemoved": removed,
                    }
                )
            removed_for_eye += removed
        if not 20 <= removed_for_eye <= 5000:
            raise RuntimeError(
                f"Socket cleanup selected an unsafe amount for side {side}: {removed_for_eye} vertices"
            )
        total_removed += removed_for_eye
        placements.append(
            {
                "side": side,
                "x": x,
                "z": z,
                "frontY": front_y,
                "localSampleCount": sample_count,
                "verticesRemoved": removed_for_eye,
                "objects": object_reports,
            }
        )
    total_after = sum(len(obj.data.vertices) for obj in meshes)
    if total_before - total_after != total_removed:
        raise RuntimeError(
            f"Total socket deletion mismatch: expected={total_removed}, actual={total_before-total_after}"
        )
    if total_removed / max(total_before, 1) > 0.08:
        raise RuntimeError(
            f"Socket cleanup removed too much of the character: {total_removed}/{total_before}"
        )
    reports.extend(placements)
    return reports, total_before, total_after, total_removed


def author_inset_eyes(character: str, frame, socket_reports):
    height = frame["height"]
    eye_material = material(
        f"{character}_InsetDarkEye", (0.0040, 0.00075, 0.00022, 1.0), 0.88, 0.018
    )
    iris_material = material(
        f"{character}_InsetIris", (0.016, 0.0043, 0.0011, 1.0), 0.82, 0.028
    )
    pupil_material = material(
        f"{character}_InsetPupil", (0.00003, 0.00003, 0.00004, 1.0), 0.96, 0.004
    )
    highlight_material = material(
        f"{character}_InsetCatchlight", (0.62, 0.53, 0.43, 1.0), 0.55, 0.10
    )
    authored = []
    placements = []
    for socket in socket_reports:
        side = int(socket["side"])
        x = float(socket["x"])
        z = float(socket["z"]) - height * 0.0006
        front_y = float(socket["frontY"])
        # Positive Y is deeper inside the head because the approved front faces -Y.
        eye_y = front_y + height * 0.00135
        iris_y = eye_y - height * 0.00018
        pupil_y = iris_y - height * 0.00014
        highlight_y = pupil_y - height * 0.00010
        authored.extend(
            [
                shape(
                    f"{character}_InsetEye_{side}",
                    (x, eye_y, z),
                    almond_points(height * 0.0086, height * 0.00335, 64),
                    eye_material,
                    "inset dark almond eye filling the cleaned socket",
                ),
                shape(
                    f"{character}_InsetIris_{side}",
                    (x, iris_y, z),
                    oval_points(height * 0.00215, height * 0.00205, 112),
                    iris_material,
                    "small dark-brown iris",
                ),
                shape(
                    f"{character}_InsetPupil_{side}",
                    (x, pupil_y, z),
                    oval_points(height * 0.00108, height * 0.00116, 104),
                    pupil_material,
                    "small black pupil",
                ),
                shape(
                    f"{character}_InsetCatchlight_{side}",
                    (
                        x - side * height * 0.00065,
                        highlight_y,
                        z + height * 0.00078,
                    ),
                    oval_points(height * 0.00028, height * 0.00032, 104),
                    highlight_material,
                    "pin-size catchlight",
                ),
            ]
        )
        placements.append(
            {
                "side": side,
                "eyeY": eye_y,
                "irisY": iris_y,
                "pupilY": pupil_y,
                "highlightY": highlight_y,
            }
        )
    if any(len(obj.data.vertices) < 101 for obj in authored):
        raise RuntimeError("An authored inset eye layer would be hidden by production capture")
    return authored, placements


def export_glb(path: pathlib.Path, meshes) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    surviving = [obj for obj in meshes if obj.type == "MESH" and len(obj.data.vertices) > 0]
    if not surviving:
        raise RuntimeError("Socket cleanup removed every mesh")
    for obj in surviving:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = surviving[0]
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
        "schemaVersion": 12,
        "character": args.character,
        "source": args.input,
        "output": str(output),
        "success": False,
        "method": "foremost pale socket vertex deletion plus inset dark almond eyes",
        "skinPatchAuthored": False,
        "visibleScleraAuthored": False,
        "approved": False,
        "humanVisualApprovalRequired": True,
    }
    try:
        clear_scene()
        meshes = import_glb(pathlib.Path(args.input))
        frame = eye_frame(meshes)
        socket_reports, total_before, total_after, total_removed = remove_failed_socket_surfaces(
            meshes, frame
        )
        authored, eye_placements = author_inset_eyes(args.character, frame, socket_reports)
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
            socketCleanup=socket_reports,
            sourceVerticesBefore=total_before,
            sourceVerticesAfter=total_after,
            sourceVerticesRemoved=total_removed,
            insetEyePlacements=eye_placements,
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
