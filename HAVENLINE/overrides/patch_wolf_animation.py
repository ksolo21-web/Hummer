#!/usr/bin/env python3
"""Patch HAVENLINE's wolf animation state machine and runtime quality gate."""
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
enemy_path = root / "scripts/actors/enemy.gd"
enemy_source = enemy_path.read_text(encoding="utf-8")


def replace_enemy(old: str, new: str) -> None:
    global enemy_source
    count = enemy_source.count(old)
    if count != 1:
        raise SystemExit(
            f"HAVENLINE wolf animation patch refused: expected one enemy match, found {count}: {old!r}"
        )
    enemy_source = enemy_source.replace(old, new, 1)


replace_enemy(
    '            _play_creature(["run", "walk"])\n',
    '            _play_creature(["gallop", "run", "walk"])\n',
)

replace_enemy(
    '''        if actor:\n            actor.play_state("run")\n''',
    '''        if actor:\n            actor.play_state("run")\n        elif kind == "wolf":\n            var locomotion_clip := creature_current_clip.to_lower()\n            if not (locomotion_clip.contains("gallop") or locomotion_clip.contains("run") or locomotion_clip.contains("walk")):\n                _play_creature(["gallop", "run", "walk"])\n''',
)

replace_enemy(
    '''            director.enemy_attack_target(self, target, damage)\n\nfunc take_damage(amount: float) -> void:\n''',
    '''            director.enemy_attack_target(self, target, damage)\n        elif kind == "wolf" and _attack_timer <= 0.24:\n            if not creature_current_clip.to_lower().contains("idle"):\n                _play_creature(["idle"])\n\nfunc take_damage(amount: float) -> void:\n''',
)

replace_enemy(
    '''    if actor:\n        actor.play_action("hurt", 0.22)\n    if health <= 0.0:\n''',
    '''    if actor:\n        actor.play_action("hurt", 0.22)\n    elif kind == "wolf" and health > 0.0:\n        _play_creature(["hitreact", "hit_react", "hit"])\n    if health <= 0.0:\n''',
)

replace_enemy(
    '''            if keywords.has("run") or keywords.has("walk"):\n                var animation := player.get_animation(best)\n''',
    '''            if keywords.has("run") or keywords.has("walk") or keywords.has("gallop") or keywords.has("idle"):\n                var animation := player.get_animation(best)\n''',
)

enemy_path.write_text(enemy_source, encoding="utf-8")

runtime_path = root / "tools/runtime_smoke.gd"
runtime_source = runtime_path.read_text(encoding="utf-8")
old_runtime_gate = '''    if wolf.creature_animation_players.is_empty() or wolf.creature_current_clip.is_empty():\n        push_error("Runtime gate found a wolf model without a resolved locomotion animation.")\n        get_tree().quit(9)\n        return\n    wolf.queue_free()\n'''
new_runtime_gate = '''    if wolf.creature_animation_players.is_empty() or wolf.creature_current_clip.is_empty():\n        push_error("Runtime gate found a wolf model without a resolved locomotion animation.")\n        get_tree().quit(9)\n        return\n    var wolf_clips: Array[String] = []\n    for animation_player in wolf.creature_animation_players:\n        for animation_name in animation_player.get_animation_list():\n            var clip_name := String(animation_name)\n            if not wolf_clips.has(clip_name):\n                wolf_clips.append(clip_name)\n    if wolf_clips.size() < 10:\n        push_error("Runtime gate requires a production wolf with at least 10 clips; found %d." % wolf_clips.size())\n        get_tree().quit(10)\n        return\n    var required_wolf_keywords: Array[String] = ["walk", "attack", "death"]\n    for required_keyword in required_wolf_keywords:\n        var found_required := false\n        for clip_name in wolf_clips:\n            if clip_name.to_lower().contains(required_keyword):\n                found_required = true\n                break\n        if not found_required:\n            push_error("Runtime gate could not find required wolf animation: %s" % required_keyword)\n            get_tree().quit(11)\n            return\n    wolf.queue_free()\n'''
if runtime_source.count(old_runtime_gate) != 1:
    raise SystemExit("HAVENLINE wolf animation patch refused: runtime wolf gate changed")
runtime_source = runtime_source.replace(old_runtime_gate, new_runtime_gate, 1)
old_print = '    print("HAVENLINE runtime gate passed: %s, %s" % [actor.animation_debug_summary(), CAPTURE_PATH])\n'
new_print = '    print("HAVENLINE runtime gate passed: %s, wolf clips=%d, %s" % [actor.animation_debug_summary(), wolf_clips.size(), CAPTURE_PATH])\n'
if runtime_source.count(old_print) != 1:
    raise SystemExit("HAVENLINE wolf animation patch refused: runtime success message changed")
runtime_path.write_text(runtime_source.replace(old_print, new_print, 1), encoding="utf-8")
