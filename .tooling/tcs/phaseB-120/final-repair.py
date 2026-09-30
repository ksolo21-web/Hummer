#!/usr/bin/env python3
from pathlib import Path
import json, sys

def one(text, old, new, label):
    n=text.count(old)
    assert n==1, f"{label}: expected 1 anchor, found {n}"
    return text.replace(old,new)

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: final-repair.py <phase-b-source>")
    root=Path(sys.argv[1]).resolve(strict=True)
    ui=root/"app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt"
    s=ui.read_text()
    old='''            NavigationRailItem(
                selected = route == destination.route,
                onClick = { onRoute(destination.route) },
                icon = { NavGlyph(destination.glyph, route == destination.route) },
                label = { Text(destination.label) }
            )
'''
    new='''            NavigationRailItem(
                selected = route == destination.route,
                onClick = { onRoute(destination.route) },
                icon = { NavGlyph(destination.glyph, route == destination.route) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag("nav-${destination.label.lowercase()}")
            )
'''
    s=one(s,old,new,"wide navigation rail destination tags")
    ui.write_text(s)
    print(json.dumps({
        "schema":"territory-phase-b-final-repair-v1",
        "wideNavigationDestinationTags":True,
        "phoneWideNavigationIdentityParity":True
    },indent=2))

if __name__=="__main__":
    main()
