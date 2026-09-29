#!/usr/bin/env python3
from pathlib import Path
import json, sys

def one(text, old, new, label):
    count=text.count(old)
    assert count==1, f"{label}: expected 1 anchor, found {count}"
    return text.replace(old,new)

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: final-repair.py <phase-a-source>")
    root=Path(sys.argv[1]).resolve(strict=True)

    ui=root/"app/src/main/java/com/koenterprises/territorycardstudio/ProductionUi.kt"
    s=ui.read_text()
    old='''                if (selected.isNotEmpty()) {
                    Text("Selected: ${selected.joinToString(" + ") { it.label }}", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("selected-create-types"))
                }
'''
    new='''                if (selected.isEmpty()) {
                    Text(
                        "Choose at least one territory type.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("create-type-required")
                    )
                } else {
                    Text("Selected: ${selected.joinToString(" + ") { it.label }}", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("selected-create-types"))
                }
'''
    s=one(s,old,new,"visible type requirement")
    ui.write_text(s)

    test=root/"app/src/androidTest/java/com/koenterprises/territorycardstudio/PhaseA110InstrumentationTest.kt"
    t=test.read_text()
    old='''    private fun apartmentItem(): TerritoryDashboardItem =
        TerritoryDashboardModel.from(app.services.knowledgeBase).items.first {
            it.assignment.identity.territoryClass == TerritoryClass.Apartment
        }
'''
    new='''    private fun apartmentItem(): TerritoryDashboardItem =
        TerritoryDashboardModel.from(app.services.knowledgeBase).items.first {
            it.assignment.identity.territoryClass == TerritoryClass.Apartment && it.assignment.needsNewCard
        }
'''
    t=one(t,old,new,"new-card apartment fixture")
    t=t.replace(
        'rule.onNodeWithTag("create-continue").performClick()',
        'rule.onNodeWithTag("create-continue").performScrollTo().performClick()'
    )
    t=t.replace(
        'rule.onNodeWithText("Choose at least one territory type.").assertIsDisplayed()',
        'rule.onNodeWithTag("create-type-required").performScrollTo().assertIsDisplayed()'
    )
    t=t.replace(
        'rule.onNodeWithTag("simple-territory-workspace").assertIsDisplayed()',
        'rule.waitForIdle()\n        rule.onNodeWithTag("simple-territory-workspace").assertIsDisplayed()'
    )
    t=t.replace(
        'rule.onNodeWithTag("selected-create-types").assertTextContains("Apartment").assertTextContains("Regular")',
        'rule.onNodeWithText("Selected: Apartment + Regular").performScrollTo().assertIsDisplayed()'
    )
    t=t.replace(
        'rule.onNodeWithTag("requested-territory-types").assertTextContains("Apartment")\\n        rule.onNodeWithTag("requested-territory-types").assertTextContains("Regular")',
        'rule.onNodeWithText("Types: Apartment + Regular").assertIsDisplayed()'
    )
    t=t.replace(
        'rule.onNodeWithTag("requested-territory-types").assertTextContains("Apartment")\n        rule.onNodeWithTag("requested-territory-types").assertTextContains("Regular")',
        'rule.onNodeWithText("Types: Apartment + Regular").assertIsDisplayed()'
    )
    test.write_text(t)

    print(json.dumps({
        "schema":"territory-phase-a-final-repair-v1",
        "visibleMinimumTypeRequirement":True,
        "newCardAcceptanceFixture":True,
        "scrollSafeCreateActions":True,
        "workspaceTransitionWaitForIdle":True
    },indent=2))

if __name__=="__main__":
    main()
