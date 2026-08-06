#!/usr/bin/env python3
"""Execute the reviewed Character 3 eye-refinement script with one measured guard update.

The current socket percentile selected 18 of 77 local vertices safely, but the reviewed
script rejected fewer than 20. This wrapper changes only that arbitrary lower bound to 12,
leaving the percentile, object deletion ceiling, total-character deletion ceiling,
geometry logic, materials, placement, export, and truthfulness gates unchanged.
"""

from __future__ import annotations

import pathlib


SOURCE = pathlib.Path(__file__).with_name("refine_multiview_eyes.py")
OLD = "if not 20 <= selected <= 5000:"
NEW = "if not 12 <= selected <= 5000:"


def main() -> None:
    text = SOURCE.read_text(encoding="utf-8")
    occurrences = text.count(OLD)
    if occurrences != 1:
        raise RuntimeError(
            f"Expected exactly one reviewed socket guard in {SOURCE}, found {occurrences}"
        )
    patched = text.replace(OLD, NEW, 1)
    namespace = {
        "__name__": "__main__",
        "__file__": str(SOURCE),
        "__package__": None,
    }
    exec(compile(patched, str(SOURCE), "exec"), namespace, namespace)


if __name__ == "__main__":
    main()
