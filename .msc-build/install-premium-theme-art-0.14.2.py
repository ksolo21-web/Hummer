#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import tempfile
from pathlib import Path

from PIL import Image


ROOT = Path('.')
THEMES = (
    'moonlit_wolf',
    'waterfall_serenity', 'rainforest_harmony', 'ocean_majesty',
    'celestial_wonder', 'mountain_sunrise', 'creation_garden',
    'bible_sketch_study', 'parable_line_panels', 'noahs_ark',
    'red_sea_deliverance', 'creation_sky', 'bible_timeline', 'bible_map',
    'lion_premium_2', 'fox_premium_2',
)
PROTECTED_THEMES = (
    'calm_light', 'premium_dark', 'warm_editorial',
    'owl', 'fox', 'lion', 'tiger', 'golden_owl', 'sakura_tiger',
)
TARGETS = {
    'phone': ROOT / 'MyStudyCompanion/app/src/main/res/drawable-nodpi',
    'wear': ROOT / 'MyStudyCompanion/wear/src/main/res/drawable-nodpi',
    'web': ROOT / 'MyStudyCompanionWeb/assets',
}
SOURCE_DIMENSIONS = (1024, 1536)
MIN_SOURCE_BYTES = 100_000


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def image_dimensions(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        image.verify()
    with Image.open(path) as image:
        return image.size


def validate_image(
    path: Path,
    *,
    expected_digest: str | None = None,
    expected_dimensions: tuple[int, int] | None = None,
    minimum_bytes: int = 1,
) -> dict[str, object]:
    if not path.is_file() or path.stat().st_size < minimum_bytes:
        raise SystemExit(f'Missing or undersized artwork: {path}')
    dimensions = image_dimensions(path)
    if expected_dimensions is not None and dimensions != expected_dimensions:
        raise SystemExit(f'Artwork dimensions changed: {path}={dimensions}')
    digest = sha256(path)
    if expected_digest is not None and digest != expected_digest:
        raise SystemExit(f'Artwork checksum changed: {path}={digest}')
    return {
        'sha256': digest,
        'bytes': path.stat().st_size,
        'dimensions': list(dimensions),
    }


def atomic_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            dir=destination.parent,
            prefix=f'.{destination.name}.',
            suffix='.tmp',
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
        shutil.copyfile(source, temporary)
        if sha256(temporary) != sha256(source):
            raise SystemExit(f'Atomic artwork copy failed verification: {destination}')
        image_dimensions(temporary)
        os.replace(temporary, destination)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def atomic_json_write(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode='w',
            encoding='utf-8',
            dir=path.parent,
            prefix=f'.{path.name}.',
            suffix='.tmp',
            delete=False,
        ) as stream:
            temporary = Path(stream.name)
            json.dump(payload, stream, indent=2, sort_keys=True)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def read_source_checksums(art_dir: Path) -> dict[str, str]:
    checksum_path = art_dir / 'SHA256SUMS.txt'
    if not checksum_path.is_file():
        raise SystemExit(f'Artwork checksum manifest is missing: {checksum_path}')
    checksums: dict[str, str] = {}
    for raw_line in checksum_path.read_text(encoding='utf-8').splitlines():
        if not raw_line.strip():
            continue
        digest, filename = raw_line.split(maxsplit=1)
        filename = filename.strip().lstrip('*')
        if Path(filename).name != filename:
            raise SystemExit(f'Unsafe artwork manifest path: {filename}')
        checksums[filename] = digest
    expected_names = {f'theme_scene_{slug}.webp' for slug in THEMES}
    if set(checksums) != expected_names:
        raise SystemExit('Premium artwork checksum manifest does not list the exact sixteen themes.')
    actual_names = {path.name for path in art_dir.glob('theme_scene_*.webp')}
    if actual_names != expected_names:
        raise SystemExit('Premium artwork directory does not contain the exact sixteen themes.')
    return checksums


def validate_source_art(art_dir: Path) -> dict[str, dict[str, object]]:
    checksums = read_source_checksums(art_dir)
    validated: dict[str, dict[str, object]] = {}
    for filename, expected_digest in sorted(checksums.items()):
        validated[filename] = validate_image(
            art_dir / filename,
            expected_digest=expected_digest,
            expected_dimensions=SOURCE_DIMENSIONS,
            minimum_bytes=MIN_SOURCE_BYTES,
        )
    return validated


def snapshot_protected(snapshot_dir: Path) -> None:
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, object] = {'version': 1, 'surfaces': {}}
    surfaces = manifest['surfaces']
    assert isinstance(surfaces, dict)
    for label, target in TARGETS.items():
        surface: dict[str, object] = {}
        protected_dir = snapshot_dir / label
        protected_dir.mkdir(parents=True, exist_ok=True)
        for slug in PROTECTED_THEMES:
            filename = f'theme_scene_{slug}.webp'
            source = target / filename
            metadata = validate_image(source, minimum_bytes=20_000)
            atomic_copy(source, protected_dir / filename)
            surface[slug] = metadata
        surfaces[label] = surface
    atomic_json_write(snapshot_dir / 'protected-theme-manifest.json', manifest)
    print('PASS: snapshotted the accepted nine themes without changing a byte.')


def load_protected_manifest(snapshot_dir: Path) -> dict[str, object]:
    path = snapshot_dir / 'protected-theme-manifest.json'
    if not path.is_file():
        raise SystemExit(f'Protected-theme snapshot is missing: {path}')
    manifest = json.loads(path.read_text(encoding='utf-8'))
    if manifest.get('version') != 1:
        raise SystemExit('Unsupported protected-theme snapshot version.')
    return manifest


def restore_and_verify_protected(snapshot_dir: Path) -> None:
    manifest = load_protected_manifest(snapshot_dir)
    surfaces = manifest.get('surfaces')
    if not isinstance(surfaces, dict) or set(surfaces) != set(TARGETS):
        raise SystemExit('Protected-theme snapshot surfaces are incomplete.')
    for label, target in TARGETS.items():
        entries = surfaces[label]
        if not isinstance(entries, dict) or set(entries) != set(PROTECTED_THEMES):
            raise SystemExit(f'Protected-theme snapshot is incomplete for {label}.')
        for slug in PROTECTED_THEMES:
            filename = f'theme_scene_{slug}.webp'
            metadata = entries[slug]
            if not isinstance(metadata, dict):
                raise SystemExit(f'Protected-theme metadata is invalid for {label}/{slug}.')
            snapshot = snapshot_dir / label / filename
            expected_digest = str(metadata.get('sha256', ''))
            validate_image(snapshot, expected_digest=expected_digest, minimum_bytes=20_000)
            atomic_copy(snapshot, target / filename)
            validate_image(target / filename, expected_digest=expected_digest, minimum_bytes=20_000)


def install_rebuilt(art_dir: Path) -> None:
    source_metadata = validate_source_art(art_dir)
    for slug in THEMES:
        filename = f'theme_scene_{slug}.webp'
        source = art_dir / filename
        expected_digest = str(source_metadata[filename]['sha256'])
        for target in TARGETS.values():
            destination = target / filename
            atomic_copy(source, destination)
            validate_image(
                destination,
                expected_digest=expected_digest,
                expected_dimensions=SOURCE_DIMENSIONS,
                minimum_bytes=MIN_SOURCE_BYTES,
            )

    print('PASS: installed sixteen checksum-locked premium scenes on phone, Wear, and web.')


def install(art_dir: Path, snapshot_dir: Path) -> None:
    restore_and_verify_protected(snapshot_dir)
    install_rebuilt(art_dir)

    restore_and_verify_protected(snapshot_dir)
    print('PASS: protected nine themes restored exactly after premium scene installation.')


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest='command', required=True)
    snapshot = subparsers.add_parser('snapshot')
    snapshot.add_argument('snapshot_dir', type=Path)
    verify = subparsers.add_parser('verify-source')
    verify.add_argument('art_dir', type=Path)
    rebuilt = subparsers.add_parser('install-rebuilt')
    rebuilt.add_argument('art_dir', type=Path)
    installer = subparsers.add_parser('install')
    installer.add_argument('art_dir', type=Path)
    installer.add_argument('snapshot_dir', type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == 'snapshot':
        snapshot_protected(args.snapshot_dir)
    elif args.command == 'verify-source':
        metadata = validate_source_art(args.art_dir)
        print(f'PASS: verified {len(metadata)} checksum-locked premium theme scenes.')
    elif args.command == 'install-rebuilt':
        install_rebuilt(args.art_dir)
    elif args.command == 'install':
        install(args.art_dir, args.snapshot_dir)


if __name__ == '__main__':
    main()
