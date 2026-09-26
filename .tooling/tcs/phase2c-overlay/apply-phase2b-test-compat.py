#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
path=root/"app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase2BWorkspaceInstrumentationTest.kt"
s=path.read_text()

old='''        composeRule.onNodeWithTag("workspace-readiness").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tab-Streets").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
        composeRule.onNodeWithTag("workspace-tab-Addresses").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-tab-Streets").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Addresses").performClick()
        composeRule.onNodeWithTag("letter-inventory-unavailable").assertIsDisplayed()
'''
new='''        composeRule.onNodeWithTag("workspace-readiness").assertIsDisplayed()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Streets"))
        composeRule.onNodeWithTag("workspace-tab-Streets").assertIsDisplayed()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-mode-Letter-Writing"))
        composeRule.onNodeWithTag("workspace-mode-Letter-Writing").performClick()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Addresses"))
        composeRule.onNodeWithTag("workspace-tab-Addresses").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-tab-Streets").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Addresses").performClick()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("letter-inventory-unavailable"))
        composeRule.onNodeWithTag("letter-inventory-unavailable").assertIsDisplayed()
'''
if s.count(old) != 1:
    raise SystemExit("regular Phase 2B compatibility anchor mismatch: "+str(s.count(old)))
s=s.replace(old,new,1)

old2='''        composeRule.onNodeWithTag("workspace-tab-Phone-List").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-mode-Letter-Writing").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Phone-List").performClick()
        composeRule.onNodeWithTag("phone-inventory-unavailable").assertIsDisplayed()
'''
new2='''        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("workspace-tab-Phone-List"))
        composeRule.onNodeWithTag("workspace-tab-Phone-List").assertIsDisplayed()
        composeRule.onAllNodesWithTag("workspace-mode-Letter-Writing").assertCountEquals(0)
        composeRule.onNodeWithTag("workspace-tab-Phone-List").performClick()
        composeRule.onNodeWithTag("territory-workspace").performScrollToNode(hasTestTag("phone-inventory-unavailable"))
        composeRule.onNodeWithTag("phone-inventory-unavailable").assertIsDisplayed()
'''
if s.count(old2) != 1:
    raise SystemExit("telephone Phase 2B compatibility anchor mismatch: "+str(s.count(old2)))
s=s.replace(old2,new2,1)

path.write_text(s)
print("PHASE2C_PHASE2B_TEST_COMPAT=PASS")
