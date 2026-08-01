#!/usr/bin/env python3
"""Patch HAVENLINE's wolf animation state machine for the pinned Ultimate wolf."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
path = root / "scripts/actors/enemy.gd"
source = path.read_text(encoding="utf-8")


def replace_exact(old: str, new: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE wolf animation patch refused: expected one match, found {count}: {old!r}"
        )
    source = source.replace(old, new, 1)


replace_exact(
    '            _play_creature(["run", "walk"])\n',
    '            _play_creature(["gallop", "run", "walk"])\n',
)

replace_exact(
    '''        if actor:\n            actor.play_state("run")\n''',
    '''        if actor:\n            actor.play_state("run")\n        elif kind == "wolf":\n            var locomotion_clip := creature_current_clip.to_lower()\n            if not (locomotion_clip.contains("gallop") or locomotion_clip.contains("run") or locomotion_clip.contains("walk")):\n                _play_creature(["gallop", "run", "walk"])\n''',
)

replace_exact(
    '''            director.enemy_attack_target(self, target, damage)\n\nfunc take_damage(amount: float) -> void:\n''',
    '''            director.enemy_attack_target(self, target, damage)\n        elif kind == "wolf" and _attack_timer <= 0.24:\n            if not creature_current_clip.to_lower().contains("idle"):\n                _play_creature(["idle"])\n\nfunc take_damage(amount: float) -> void:\n''',
)

replace_exact(
    '''    if actor:\n        actor.play_action("hurt", 0.22)\n    if health <= 0.0:\n''',
    '''    if actor:\n        actor.play_action("hurt", 0.22)\n    elif kind == "wolf" and health > 0.0:\n        _play_creature(["hitreact", "hit_react", "hit"])\n    if health <= 0.0:\n''',
)

replace_exact(
    '''            if keywords.has("run") or keywords.has("walk"):\n                var animation := player.get_animation(best)\n''',
    '''            if keywords.has("run") or keywords.has("walk") or keywords.has("gallop") or keywords.has("idle"):\n                var animation := player.get_animation(best)\n''',
)

path.write_text(source, encoding="utf-8")
