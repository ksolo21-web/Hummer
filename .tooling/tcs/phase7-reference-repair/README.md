# Phase 7: reference comparison save repair

Phase 7 remains OPEN, with 0/4 real cards approved. This isolated software repair does not replace or pass the four-card acceptance gate.

Exact baseline: 9722197c3649d74dd30b093e3aff1a0ae5f1ba79, source recovered from run 36500257533. The actual failure stack is NativeAssignmentCodec.s -> building -> encode -> AndroidNativeDraftStore.save. The six installed A265 buildings contain Unicode en dashes unsupported by the intentionally ASCII-only codec and embedded font.

NativeReferenceComparison proposes only known range-punctuation replacements, before hashes are calculated. It preserves numeric members, geometry, work rules and immutable catalog bytes. All comparison observations remain unconfirmed. Unknown, control and invisible text is rejected with the exact field. No receipt, codec, renderer or authority policy is loosened.

The native UI now uses this producer for its existing comparison action. Ten standalone Kotlin checks passed locally using actual check() assertions and an annotation-only shim; this was NOT a JUnit-runner result. The workflow runs real JUnit plus actual A265 UI save/reload in phone/wide Light/Dark. A passing result here proves software persistence, not current map accuracy.

## Source-reconciliation defects still open

The older Phase7A265CandidateInstrumentationTest copies six green catalog buildings and confirms generic correspondence without proving current placement. It omits the three red exclusion footprints visible in the locked A265 PDF. Its gray road incorrectly sets accessOnly=true (that flag requires red), and it expects a source image digest where registration actually returns the reconciliation digest. Its lone hardcoded road is not aligned with the copied building cluster. These must not be hidden by changing assertions or counting that candidate as an approved card. A265 source geometry, complete context/exclusions, current GIS, final PDF review, separate critic, approval and final save/readback remain required.

269 needs its catalog/source identity conflict resolved; historical A296 must respect the authorized combined A295; clipped Livernois/Willow Grove needs a proven complete assignment source. Earlier six reserved assignments remain untouched. No separate critic runtime was available for this repair; review so far is explicitly internal.

The original 01-09 patch chain remains unchanged. recover.sh rebuilds it and then apply.py verifies every old/new source hash before and after applying the follow-on repair. The fully integrated ordinary source is retained in the workflow artifact.
