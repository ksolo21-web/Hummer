#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib

from PIL import Image, ImageEnhance, ImageFilter, ImageOps


def patch_triposr_texture_baker(repo_root: pathlib.Path = pathlib.Path("triposr")) -> dict:
    """Force TripoSR texture baking onto Mesa EGL instead of an X display."""

    baker = repo_root / "tsr" / "bake_texture.py"
    if not baker.is_file():
        raise FileNotFoundError(f"TripoSR texture baker is missing: {baker}")

    original = baker.read_text(encoding="utf-8")
    original_sha256 = hashlib.sha256(original.encode("utf-8")).hexdigest()
    marker = "def _havenline_create_headless_context():"
    replaced_call = "    ctx = moderngl.create_context(standalone=True)\n"
    replacement_call = "    ctx = _havenline_create_headless_context()\n"
    patched = False

    if marker not in original:
        helper = '''\n\ndef _havenline_create_headless_context():\n    # Mesa's software EGL path is deterministic on GitHub's Ubuntu runner and does not\n    # require DISPLAY. Keep a diagnostic fallback so failures identify every attempted\n    # backend instead of surfacing the misleading XOpenDisplay exception alone.\n    os.environ.setdefault("LIBGL_ALWAYS_SOFTWARE", "1")\n    os.environ.setdefault("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe")\n    errors = []\n    try:\n        return moderngl.create_standalone_context(backend="egl")\n    except Exception as exception:\n        errors.append(f"egl: {exception!r}")\n    try:\n        return moderngl.create_context(standalone=True)\n    except Exception as exception:\n        errors.append(f"default: {exception!r}")\n    raise RuntimeError(\n        "Unable to create a headless ModernGL texture-baking context; "\n        + " | ".join(errors)\n    )\n'''
        import_anchor = "import moderngl\n"
        if import_anchor not in original:
            raise RuntimeError(f"Unexpected TripoSR texture-baker imports in {baker}")
        original = original.replace(import_anchor, import_anchor + "import os\n", 1)
        original = original.replace("from PIL import Image\n", "from PIL import Image\n" + helper, 1)
        if replaced_call not in original:
            raise RuntimeError(
                "TripoSR texture-baker context call changed upstream; refusing an unsafe patch"
            )
        original = original.replace(replaced_call, replacement_call, 1)
        baker.write_text(original, encoding="utf-8")
        patched = True
    elif replacement_call not in original:
        raise RuntimeError("TripoSR texture baker contains the Havenline marker but not its context call")

    final_payload = baker.read_bytes()
    return {
        "schemaVersion": 1,
        "textureBaker": str(baker),
        "upstreamSha256": original_sha256,
        "patchedSha256": hashlib.sha256(final_payload).hexdigest(),
        "patchedDuringThisRun": patched,
        "preferredBackend": "egl",
        "softwareRenderer": "llvmpipe",
        "displayRequired": False,
    }


def patch_triposr_textured_glb_exporter(
    repo_root: pathlib.Path = pathlib.Path("triposr"),
) -> dict:
    """Repair TripoSR's textured ``--model-save-format glb`` export path.

    Upstream routes every baked-texture mesh through ``xatlas.export``. That function
    writes OBJ text even when the destination is named ``mesh.glb``. The file therefore
    has an invalid GLB header and every later Blender/Unity stage fails. Keep xatlas for
    its UV export, then immediately repackage that OBJ data and the baked texture into a
    real binary GLB using trimesh.
    """

    run_file = repo_root / "run.py"
    if not run_file.is_file():
        raise FileNotFoundError(f"TripoSR runner is missing: {run_file}")

    original = run_file.read_text(encoding="utf-8")
    original_sha256 = hashlib.sha256(original.encode("utf-8")).hexdigest()
    marker = "# HAVENLINE_TEXTURED_GLB_NORMALIZER_V1"
    patched = False

    if marker not in original:
        import_anchor = "import xatlas\n"
        if import_anchor not in original:
            raise RuntimeError(f"Unexpected TripoSR imports in {run_file}")
        original = original.replace(import_anchor, import_anchor + "import trimesh\n", 1)

        export_anchor = '''        xatlas.export(out_mesh_path, meshes[0].vertices[bake_output["vmapping"]], bake_output["indices"], bake_output["uvs"], meshes[0].vertex_normals[bake_output["vmapping"]])
        Image.fromarray((bake_output["colors"] * 255.0).astype(np.uint8)).transpose(Image.FLIP_TOP_BOTTOM).save(out_texture_path)
'''
        export_replacement = export_anchor + '''        if args.model_save_format == "glb":
            # HAVENLINE_TEXTURED_GLB_NORMALIZER_V1
            textured_scene = trimesh.load(
                out_mesh_path,
                file_type="obj",
                force="scene",
                process=False,
            )
            texture_image = Image.open(out_texture_path).convert("RGBA")
            if not textured_scene.geometry:
                raise RuntimeError("TripoSR produced no geometry before GLB normalization")
            for geometry in textured_scene.geometry.values():
                uv = getattr(geometry.visual, "uv", None)
                if uv is None or len(uv) != len(geometry.vertices):
                    raise RuntimeError(
                        "TripoSR textured OBJ is missing one UV coordinate per vertex"
                    )
                geometry.visual = trimesh.visual.texture.TextureVisuals(
                    uv=uv,
                    image=texture_image.copy(),
                )
            glb_payload = textured_scene.export(file_type="glb")
            if not isinstance(glb_payload, (bytes, bytearray)) or glb_payload[:4] != b"glTF":
                raise RuntimeError("Normalized TripoSR output is not a binary GLB")
            temporary_glb = out_mesh_path + ".normalized"
            with open(temporary_glb, "wb") as output_handle:
                output_handle.write(glb_payload)
            os.replace(temporary_glb, out_mesh_path)
'''
        if export_anchor not in original:
            raise RuntimeError(
                "TripoSR baked-texture export changed upstream; refusing an unsafe patch"
            )
        original = original.replace(export_anchor, export_replacement, 1)
        run_file.write_text(original, encoding="utf-8")
        patched = True
    elif "textured_scene = trimesh.load(" not in original:
        raise RuntimeError("TripoSR runner contains the Havenline marker but not its GLB conversion")

    final_payload = run_file.read_bytes()
    return {
        "schemaVersion": 1,
        "runner": str(run_file),
        "upstreamSha256": original_sha256,
        "patchedSha256": hashlib.sha256(final_payload).hexdigest(),
        "patchedDuringThisRun": patched,
        "rootCause": "xatlas.export writes OBJ text regardless of a .glb destination suffix",
        "normalizedContainer": "binary glTF 2.0 GLB",
        "embeddedTexture": True,
        "expectedMagic": "glTF",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", required=True)
    parser.add_argument("--sheet", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source = pathlib.Path(args.sheet)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not source.is_file() or source.stat().st_size == 0:
        raise FileNotFoundError(source)

    image = Image.open(source).convert("RGB")
    width, height = image.size
    # The approved turnaround sheets place the unobstructed three-quarter front view
    # in the left quarter. Exclude the title and bottom view label before reconstruction.
    crop = image.crop(
        (
            int(width * 0.015),
            int(height * 0.075),
            int(width * 0.295),
            int(height * 0.875),
        )
    )
    crop = ImageOps.contain(crop, (896, 896), Image.Resampling.LANCZOS)
    crop = ImageEnhance.Contrast(crop).enhance(1.04)
    crop = ImageEnhance.Sharpness(crop).enhance(1.06)
    crop = crop.filter(ImageFilter.UnsharpMask(radius=1.1, percent=80, threshold=3))
    canvas = Image.new("RGB", (896, 896), (244, 246, 249))
    canvas.paste(crop, ((896 - crop.width) // 2, (896 - crop.height) // 2))
    canvas.save(output, quality=96, subsampling=0, optimize=True)

    texture_patch_report = patch_triposr_texture_baker()
    glb_patch_report = patch_triposr_textured_glb_exporter()
    report = {
        "schemaVersion": 3,
        "character": args.character,
        "source": str(source),
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "sourceSize": [width, height],
        "cropFractions": [0.015, 0.075, 0.295, 0.875],
        "output": str(output),
        "outputBytes": output.stat().st_size,
        "outputSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "approvedTurnaroundView": "three-quarter-front",
        "headlessTextureBakerPatch": texture_patch_report,
        "texturedGlbExporterPatch": glb_patch_report,
    }
    output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
