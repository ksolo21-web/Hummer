#!/usr/bin/env python3
"""Fetch checksum-pinned stylized production assets for HAVENLINE."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

MIRROR_REPO = "https://github.com/kirbycope/godot-3d-player-controller-v2.git"
MIRROR_COMMIT = "a928cfa67684352b75a65c510d8751d1f3f2489c"
MIRROR_PATHS = (
    "assets/universal_base_characters",
    "assets/universal_animation_library",
    "assets/universal_animation_library_2",
)
SURVIVAL_URL = "https://opengameart.org/sites/default/files/survival_pack_-_sept_2020.zip"
NATURE_URL = "https://opengameart.org/sites/default/files/stylized_nature_megakitstandard.zip"
FURNACE_URL = "https://raw.githubusercontent.com/ToxSam/cc0-models-Polygonal-Mind/main/projects/christmas/Fireplace.glb"
WOLF_WHEEL_URL = (
    "https://files.pythonhosted.org/packages/16/a0/"
    "3a0a2b8ee12d27e64a898a6a5f08820029d12afa36b794e663cc53537c32/"
    "animasim-0.2.1-py3-none-any.whl"
)
WOLF_WHEEL_SHA256 = "d111ffc9782f09872846b09ac7868d41e126ef72e3153d9d0173d401be3277a3"
WOLF_WHEEL_BYTES = 13_917_433
WOLF_ARCHIVE_PATH = "animasim/_assets/glb/wolf.glb"

EXPECTED = {
    "animation_library_1": (8_114_376, "5e7f0efca238924037fd9659e56ef1c4aebde269d8b1f1800b330887907c198c"),
    "animation_library_2": (8_061_600, "9a0ffda4931f934f13fb584002c51673723b03f9655a581167e7e5dae744f086"),
    "axe": (20_620, "41537fbef651a0931b42b1786658ea1ef116dbe4a2315a38d12644251e4c52f4"),
    "backpack": (48_844, "1e0f2d1d7d3063daf78abe727da0e4486717e9c3d3fe5a4a660d2cade829b70d"),
    "campfire": (28_060, "cf17677ab969bf5aa84faa1743e8ae974add5bdc9da4f97fbc2223a616de69fd"),
    "furnace": (412_452, "df5a90e160769ee4f5f8fb39e828f4b84378a97e9a126a457184318f51685a31"),
    "log": (19_836, "a758ac8c0f236a82c8af8a1db1d235d254325a9b3b0c065d8c1fbe8c5b37814d"),
    "pine_a": (205_036, "1ec8ee0339965d4249aa30ccba497356c10bcabd73d3e92163e32c3110f8025a"),
    "pine_b": (263_724, "434578608ba1f2ee8ab35eda27c941e5f4b133bbe83633bc83657f6afba7896d"),
    "rock_a": (27_164, "178576f884fdcc3a5c3cd824dbdceb54c8b96c791d06a69045d6553bf65cc526"),
    "rock_b": (37_052, "f904be29181f09fb783be55b3ce412e4afb92919c02e291db570641ef0c3e986"),
    "tent": (29_244, "621e0dcedb0bc4cffa9999c7e7b2b2ed83778bad28dccedd0606a1aa6a9b9f4d"),
    "wolf": (1_984_192, "aa06297d0e66568711885178d1d35e2ca1e392dceb05f988df0497de0274a705"),
}

class AssetError(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(*args: str, cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    print("+", " ".join(args), flush=True)
    subprocess.run(args, cwd=cwd, env=env, check=True)


def download(url: str, destination: Path, retries: int = 5) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.stat().st_size > 1024:
        return destination
    headers = {"User-Agent": "HAVENLINE-production-assets/1.0"}
    last_error: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as output:
                shutil.copyfileobj(response, output)
            if destination.stat().st_size < 1024:
                raise AssetError(f"Downloaded file is unexpectedly small: {destination}")
            return destination
        except Exception as exc:
            last_error = exc
            destination.unlink(missing_ok=True)
            if attempt < retries:
                time.sleep(attempt * 4)
    raise AssetError(f"Failed to download {url}: {last_error}")


def safe_extract(archive: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    with zipfile.ZipFile(archive) as zf:
        root = destination.resolve()
        for member in zf.infolist():
            target = (destination / member.filename).resolve()
            if target != root and root not in target.parents:
                raise AssetError(f"Unsafe archive member: {member.filename}")
        zf.extractall(destination)


def copy_tree_without_cache(source: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    for path in source.rglob("*"):
        if path.is_dir() or path.suffix in {".import", ".uid"} or ".godot" in path.parts:
            continue
        target = destination / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)


def write_character_scene(destination: Path, root_name: str, body: str, eyebrows: str, hair: str) -> None:
    base = "res://assets/universal_base_characters/"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        f'''[gd_scene load_steps=4 format=3]\n\n[ext_resource type="PackedScene" path="{base}{body}" id="1_body"]\n[ext_resource type="PackedScene" path="{base}{eyebrows}" id="2_brows"]\n[ext_resource type="PackedScene" path="{base}{hair}" id="3_hair"]\n\n[node name="{root_name}" type="Node3D"]\n\n[node name="Body" parent="." instance=ExtResource("1_body")]\ntransform = Transform3D(-1, 0, -0.00000008742278, 0, 1, 0, 0.00000008742278, 0, -1, 0, 0, 0)\n\n[node name="Eyebrows" parent="." instance=ExtResource("2_brows")]\ntransform = Transform3D(-1, 0, -0.00000008742278, 0, 1, 0, 0.00000008742278, 0, -1, 0, 0, 0)\n\n[node name="Hair" parent="." instance=ExtResource("3_hair")]\ntransform = Transform3D(-1, 0, -0.00000008742278, 0, 1, 0, 0.00000008742278, 0, -1, 0, 0, 0)\n''',
        encoding="utf-8",
    )


def fetch_characters(project: Path, cache: Path, report: dict) -> dict[str, str]:
    checkout = cache / "character-mirror"
    if checkout.exists():
        shutil.rmtree(checkout)
    checkout.mkdir(parents=True)
    run("git", "init", "-q", cwd=checkout)
    run("git", "remote", "add", "origin", MIRROR_REPO, cwd=checkout)
    run("git", "sparse-checkout", "init", "--cone", cwd=checkout)
    run("git", "sparse-checkout", "set", *MIRROR_PATHS, cwd=checkout)
    env = os.environ.copy(); env["GIT_LFS_SKIP_SMUDGE"] = "1"
    run("git", "fetch", "--depth", "1", "origin", MIRROR_COMMIT, cwd=checkout, env=env)
    run("git", "checkout", "--detach", "FETCH_HEAD", cwd=checkout, env=env)
    run("git", "lfs", "install", "--local", cwd=checkout)
    run("git", "lfs", "pull", "--include=" + ",".join(f"{p}/**" for p in MIRROR_PATHS), cwd=checkout)
    for name in ("universal_base_characters", "universal_animation_library", "universal_animation_library_2"):
        copy_tree_without_cache(checkout / "assets" / name, project / "assets" / name)
    write_character_scene(project / "assets/universal_base_characters/male.tscn", "Male", "Base Characters/Superhero_Male_FullBody.gltf", "Hairstyles/Rigged/Eyebrows_Regular.gltf", "Hairstyles/Rigged/Hair_SimpleParted.gltf")
    write_character_scene(project / "assets/universal_base_characters/female.tscn", "Female", "Base Characters/Superhero_Female_FullBody.gltf", "Hairstyles/Rigged/Eyebrows_Female.gltf", "Hairstyles/Rigged/Hair_Long.gltf")
    report["sources"].append({"name": "Quaternius Universal Base Characters and Animation Libraries", "source": MIRROR_REPO, "commit": MIRROR_COMMIT, "license": "CC0 1.0"})
    return {
        "player_character": "res://assets/universal_base_characters/male.tscn",
        "guard_character": "res://assets/universal_base_characters/female.tscn",
        "animation_library_1": "res://assets/universal_animation_library/UAL1.glb",
        "animation_library_2": "res://assets/universal_animation_library_2/UAL2.glb",
    }


def find_exact(root: Path, relative_suffix: str) -> Path:
    normalized = relative_suffix.replace("\\", "/").lower()
    matches = [p for p in root.rglob("*") if p.is_file() and p.as_posix().lower().endswith(normalized)]
    if len(matches) != 1:
        raise AssetError(f"Expected one asset ending with {relative_suffix}, found {len(matches)}")
    return matches[0]


def copy_model_and_textures(pack_root: Path, model: Path, output_root: Path) -> Path:
    for image in pack_root.rglob("*"):
        if image.is_file() and image.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".tga"}:
            target = output_root / image.relative_to(pack_root)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(image, target)
    target_model = output_root / model.relative_to(pack_root)
    target_model.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(model, target_model)
    return target_model


def scene_node(name: str, resource_id: str, position: tuple[float, float, float], rotation: tuple[float, float, float], scale: tuple[float, float, float]) -> str:
    return (
        f'\n[node name="{name}" parent="." instance=ExtResource("{resource_id}")]\n'
        f'position = Vector3({position[0]:.3f}, {position[1]:.3f}, {position[2]:.3f})\n'
        f'rotation = Vector3({rotation[0]:.5f}, {rotation[1]:.5f}, {rotation[2]:.5f})\n'
        f'scale = Vector3({scale[0]:.3f}, {scale[1]:.3f}, {scale[2]:.3f})\n'
    )


def write_composites(project: Path, manifest: dict[str, str]) -> None:
    destination = project / "assets/final/environment"
    destination.mkdir(parents=True, exist_ok=True)
    log_res = manifest["log"]; backpack_res = manifest["backpack"]
    supply = ['[gd_scene load_steps=3 format=3]\n', f'[ext_resource type="PackedScene" path="{log_res}" id="1_log"]\n', f'[ext_resource type="PackedScene" path="{backpack_res}" id="2_pack"]\n', '\n[node name="SupplyCache_Final" type="Node3D"]\n']
    for index in range(8):
        supply.append(scene_node(f"FoundationLog{index:02d}", "1_log", ((index % 4 - 1.5) * 0.38, 0.16 + (index % 2) * 0.04, (index // 4 - 0.5) * 0.52), (0.0, 0.18 * (index % 3), 1.5708), (0.78, 0.78, 0.78)))
    for index, position in enumerate([(-0.42,0.42,-0.05),(0.08,0.45,0.12),(0.48,0.43,-0.12),(-0.08,0.72,-0.02)]):
        supply.append(scene_node(f"SupplyPack{index:02d}", "2_pack", position, (0.0,-0.65+index*0.42,0.0), (0.72,0.72,0.72)))
    supply_path = destination / "supply_cache.tscn"; supply_path.write_text("".join(supply), encoding="utf-8")
    fence = ['[gd_scene load_steps=2 format=3]\n', f'[ext_resource type="PackedScene" path="{log_res}" id="1_log"]\n', '\n[node name="LogBarricade_Final" type="Node3D"]\n']
    for index in range(9):
        fence.append(scene_node(f"WallLog{index:02d}", "1_log", ((index-4)*0.34,0.34+(index%2)*0.23,0.0),(0.0,0.0,1.5708),(0.76,0.76,0.76)))
    fence.append(scene_node("CrossBraceLeft", "1_log", (-0.95,0.62,0.05),(0.0,0.0,0.78),(0.82,0.82,0.82)))
    fence.append(scene_node("CrossBraceRight", "1_log", (0.95,0.62,0.05),(0.0,0.0,-0.78),(0.82,0.82,0.82)))
    fence.append(scene_node("TopRail", "1_log", (0.0,1.02,0.02),(0.0,0.0,1.5708),(1.05,1.05,1.05)))
    fence_path = destination / "log_barricade.tscn"; fence_path.write_text("".join(fence), encoding="utf-8")
    manifest["crate"] = "res://assets/final/environment/supply_cache.tscn"
    manifest["fence"] = "res://assets/final/environment/log_barricade.tscn"


def fetch_environment(project: Path, cache: Path, report: dict) -> dict[str, str]:
    survival_zip = download(SURVIVAL_URL, cache / "survival.zip")
    nature_zip = download(NATURE_URL, cache / "nature.zip")
    survival = cache / "survival-expanded"; nature = cache / "nature-expanded"
    safe_extract(survival_zip, survival); safe_extract(nature_zip, nature)
    selections = {
        "tent": (survival, "Survival Pack - Sept 2020/FBX/Tent.fbx", project / "assets/vendor/survival"),
        "campfire": (survival, "Survival Pack - Sept 2020/FBX/Bonfire.fbx", project / "assets/vendor/survival"),
        "backpack": (survival, "Survival Pack - Sept 2020/FBX/Backpack.fbx", project / "assets/vendor/survival"),
        "axe": (survival, "Survival Pack - Sept 2020/FBX/Axe.fbx", project / "assets/vendor/survival"),
        "log": (survival, "Survival Pack - Sept 2020/FBX/WoodLog.fbx", project / "assets/vendor/survival"),
        "pine_a": (nature, "FBX/Pine_2.fbx", project / "assets/vendor/nature"),
        "pine_b": (nature, "FBX/Pine_3.fbx", project / "assets/vendor/nature"),
        "rock_a": (nature, "FBX/Rock_Medium_2.fbx", project / "assets/vendor/nature"),
        "rock_b": (nature, "FBX/Rock_Medium_3.fbx", project / "assets/vendor/nature"),
    }
    manifest: dict[str, str] = {}
    for key, (pack_root, suffix, output_root) in selections.items():
        source = find_exact(pack_root, suffix)
        target = copy_model_and_textures(pack_root, source, output_root)
        manifest[key] = "res://" + target.relative_to(project).as_posix()
    download(FURNACE_URL, project / "assets/final/props/furnace.glb")
    manifest["furnace"] = "res://assets/final/props/furnace.glb"
    wheel = download(WOLF_WHEEL_URL, cache / "animasim.whl")
    if wheel.stat().st_size != WOLF_WHEEL_BYTES or sha256(wheel) != WOLF_WHEEL_SHA256:
        raise AssetError("Pinned animated-wolf wheel failed integrity validation")
    wolf = project / "assets/final/animals/wolf.glb"; wolf.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(wheel) as archive:
        with archive.open(WOLF_ARCHIVE_PATH) as source, wolf.open("wb") as output:
            shutil.copyfileobj(source, output)
    manifest["wolf"] = "res://assets/final/animals/wolf.glb"
    write_composites(project, manifest)
    report["sources"].extend([
        {"name":"Quaternius Survival Pack","source":SURVIVAL_URL,"license":"CC0 1.0"},
        {"name":"Quaternius Stylized Nature MegaKit","source":NATURE_URL,"license":"CC0 1.0"},
        {"name":"Polygonal Mind Fireplace","source":FURNACE_URL,"license":"CC0 1.0"},
        {"name":"Quaternius Ultimate Animated Animal Pack Wolf","source":WOLF_WHEEL_URL,"license":"CC0 1.0","animation_clips":12},
    ])
    return manifest


def validate_expected(project: Path, manifest: dict[str, str]) -> dict:
    files: dict = {}
    for key, resource_path in sorted(manifest.items()):
        path = project / resource_path.removeprefix("res://")
        if not path.exists():
            raise AssetError(f"Manifest path does not exist: {resource_path}")
        size = path.stat().st_size; digest = sha256(path)
        if key in EXPECTED and (size, digest) != EXPECTED[key]:
            raise AssetError(f"Pinned asset mismatch for {key}: {size} bytes, {digest}")
        files[key] = {"path":resource_path,"bytes":size,"sha256":digest}
    return files


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("project"); parser.add_argument("--clean-cache", action="store_true")
    args = parser.parse_args(); project = Path(args.project).resolve(); cache = project / ".asset-cache"
    if args.clean_cache and cache.exists(): shutil.rmtree(cache)
    cache.mkdir(parents=True, exist_ok=True)
    report: dict = {"schema":2,"sources":[]}
    manifest = fetch_characters(project, cache, report)
    manifest.update(fetch_environment(project, cache, report))
    report["manifest"] = manifest; report["files"] = validate_expected(project, manifest)
    final = project / "assets/final"; final.mkdir(parents=True, exist_ok=True)
    (final / "asset_manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    (project / "assets/ASSET_BUILD_REPORT.json").write_text(json.dumps(report, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    print(f"HAVENLINE stylized production asset gate resolved {len(manifest)} assets")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssetError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"HAVENLINE ASSET BUILD FAILED: {exc}", file=sys.stderr)
        raise SystemExit(2)
