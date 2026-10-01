# Territory Android 1.5.0 — Real Builder Contract

Status: ACTIVE REPLACEMENT ARCHITECTURE
Previous 1.4.0 Simple Create behavior is rejected as insufficient.

## Product requirement
"Create Card From This Map" must perform the same territory-building workflow used in the approved Territory system. It must not:
- paste the uploaded map into the A33/R48 shell and call that a build;
- dump raw CV fragments such as "Unresolved road N" back to the user;
- require the user to manually make routine road/work-rule/layout decisions that the builder is supposed to make.

## Required execution chain
1. Lock territory identity, mode, source bytes/hash, current active Territory skill revision.
2. Analyze the exact source map.
3. Reconcile current geography/street names using independent current sources.
4. Interpret assignment semantics:
   - green = work both sides;
   - yellow = territory-facing side only with explicit side;
   - red = do not work/access only;
   - non-road boundary preserved as boundary, not invented road.
5. Produce a structured authoritative assignment:
   - junction-bounded road segments;
   - names;
   - endpoints;
   - work status/role;
   - perimeter worked side;
   - buildings/sites when applicable;
   - access/navigation context.
6. Select card treatment automatically:
   - normal;
   - enlarged/detail;
   - numbered legend/key;
   - apartment/condo/building-number treatment;
   - split/detail where required.
7. Build a NEW measurable vector/PDF candidate with A33/R48/R52 styling and canonical identity.
8. Verify **at least two distinct major cross roads are physically drawn and readably labeled on the FRONT-PAGE MAP AREA**, with a verified connected approach into the territory. This gate is mandatory and cannot be skipped. Directions text, sidebar text, metadata, a back page, or floating road names do not count. Prefer both roads in the main map; a front-page LOCATION inset is allowed only if main-map scale would otherwise become unreadable, and the inset must visibly show both roads plus the connected approach.
9. Run deterministic source/topology/overlap/building/label/PDF gates.
10. Freeze candidate.
11. Run critic-only review on fresh full-page + 2x + overlapping 4x evidence.
12. Repair and rebuild until every mandatory category is strictly >9.0, target 10/10.
13. Re-run exact-saved-PDF final review before allowing export/approval.

## AI reasoning requirement
The deterministic Android code is not sufficient to infer the same decisions made by the Territory builder. 1.5.0 therefore requires a Territory Build Agent that receives:
- exact uploaded source;
- active Territory builder/critic contracts;
- territory KB context;
- current live verification evidence;
- existing CV/OCR proposals only as non-authoritative hints.

The agent returns a strict structured build plan. Deterministic code validates and renders that plan. The model is never allowed to bypass validators or directly release a card.

## T250 hard regression
Fixture intent: raw 250T source -> new current-standard candidate.

A valid result must:
- be materially different from merely embedding the source image;
- identify the assigned residential Telephone territory;
- preserve/derive the correct source relationships;
- identify S Rochester Rd and W Avon Rd as major location context **and physically draw/readably label both on the front-page map area** with a connected approach; directions text alone does not satisfy this gate;
- identify Meadowfield Dr and the interior Meadowfield streets;
- classify boundary/interior/access work rules correctly;
- contain no ordinary house footprints/numbers;
- use the A33/R48 family shell;
- include directions and north/location treatment;
- contain no "Unresolved road" placeholders;
- require no routine per-road manual reconciliation from the user;
- pass the full critic/review-repair chain.

## Release veto
If the app cannot run its reasoning agent, it must say the builder is unavailable. It must NOT silently fall back to template-only composition and must NOT claim the territory was built.

