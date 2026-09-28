# Create a territory card from a map picture

Phase 6 passed independent application review (9.5/10). This Android debug build is for testing; production signing and real-card commissioning remain separate.

1. On the dashboard, use **Create from a map picture**. Enter the territory number (including a lowercase split suffix if needed), choose its territory type, and select **Continue to map picture**. The number/type must already exist in the territory catalog.
2. Choose the appropriate workspace mode and **Import Map**. Select a clear, marked PNG/JPEG map or a single-page map PDF using Android's file picker.
3. Open native authoring and select **Generate card draft from picture**. Recognition runs on the device. Review the proposed roads, work colors, building footprints and member labels against the source.
4. Enter the current locality, update date, directions and reconciliation information. Correct missing names and inside-only sides. Unresolved geometry or work-color findings block registration; provide a clearer source and use **Preserve draft and generate again…**. Recognition does not establish assignment authority.
5. For Letter Writing or Telephone, import the authorized contact source and enter sourced records. Telephone numbers and unavailable-number status require separate source verification; the map does not supply telephone authorization.
6. Review complete source coverage, save the reconciliation draft, explicitly register the local assignment, and verify fresh sources to prepare it.
7. Build the card (and page 2 when applicable), inspect the actual PDF, explicitly approve that exact candidate, then validate and save through Android's file picker. The app reads the saved bytes back and can export a separate audit record.

Editing the source or assignment invalidates dependent preparation and approval. A resumed draft needs fresh preparation and explicit approval. The final PDF limit is strictly less than 300,000 bytes.

Supported-input limits: unclear or unmarked maps may not produce a usable proposal. Multi-page automatic interpretation and enrollment of new catalog identities are not supported. The six reserved real assignments remain for the user to create; synthetic regression cards do not certify their geography or contacts.
