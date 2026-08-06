#!/usr/bin/env python3
"""Run the HAVENLINE rig/export stage without its legacy in-process EEVEE proofs.

The final visual proofs are rendered from the exported production GLB by the dedicated
CPU-safe proof renderer. Keeping export and proof as separate processes prevents a
headless graphics failure from discarding an otherwise valid rig, FBX, GLB, or LOD set.
"""

from __future__ import annotations

import pathlib
import sys

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import rig_animate_character as implementation


def skip_legacy_proofs(root, objects):
    print(
        "Skipping legacy in-process EEVEE proofs; "
        "render_character_proofs_cpu.py will render the exact exported production GLB."
    )


implementation.render_proofs = skip_legacy_proofs

if __name__ == "__main__":
    implementation.main()
