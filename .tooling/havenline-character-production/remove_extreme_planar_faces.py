#!/usr/bin/env python3
"""Run the pinned floor-cleanup implementation after verifying its transfer repair."""

from pathlib import Path

implementation = Path(__file__).with_name("remove_extreme_planar_faces_impl.py.txt")
source = implementation.read_text(encoding="utf-8")
broken = 'raise RuntimeError(FCleanup removed the complete character mesh")'
fixed = 'raise RuntimeError("Cleanup removed the complete character mesh")'
if source.count(broken) != 1:
    raise RuntimeError("Pinned floor-cleanup implementation did not match its expected transfer signature")
source = source.replace(broken, fixed)
compile(source, str(implementation), "exec")
namespace = {"__name__": "__main__", "__file__": str(implementation)}
exec(compile(source, str(implementation), "exec"), namespace)
