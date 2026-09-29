#!/usr/bin/env python3
"""The positive control must retain all three roads visibly present in its source."""
from pathlib import Path
import hashlib,sys
p=Path(sys.argv[1])/'app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7SourceSufficiencyInstrumentationTest.kt'
s=p.read_text();before='fcaf5f43671e14ffa4e91e1c2aa9da79e584212b7e704e91f0bb6466c6a28fa9'
assert hashlib.sha256(p.read_bytes()).hexdigest()==before,'Positive control changed'
start=s.index('    private fun seed(');end=s.index('    private fun output(',start)
s=s[:start]+'''    private fun seed(x:Phase6NativeFixture):NativeAuthoringDraft {
        val source=x.sources.importFromStream(x.slot,"positive-control.pdf","application/pdf",x.sourcePdf().inputStream())
        val roads=listOf(
            RoadGeometry("alpha","Alpha Rd","alpha rd","yellow","perimeter","right",false,"junction","junction",4.0,listOf(Point2D(225.0,115.0),Point2D(650.0,115.0))),
            RoadGeometry("beta","Beta Dr","beta dr","green","interior","",false,"termination","termination",4.0,listOf(Point2D(225.0,200.0),Point2D(650.0,200.0))),
            RoadGeometry("gamma","Gamma Ct","gamma ct","red","excluded","",false,"termination","termination",4.0,listOf(Point2D(225.0,285.0),Point2D(650.0,285.0))))
        val a=CurrentAuthoritativeAssignmentState(x.id,x.identity,x.identity.canonicalFilename,x.kb.revision,"current_authoritative_assignment","0".repeat(64),source.sourceFilename,
            "Oakland Township","9/29/2026",listOf("Directions: Synthetic positive control only. NOT FOR FIELD USE."),"full_map",x.slot.housingType,
            RenderCoordinateSpace.LOCKED_R48_PAGE_POINTS_TOP_ORIGIN,roads,emptyList())
        val r=NativeSourceReconciliation(x.id,x.mode.name,x.kb.revision,source.sha256,x.slot.referenceSha256,"current_assignment_map","Synthetic source reviewer","2026-09-29T12:00:00Z",
            NativeSourceReconciliationContract.assignmentContentSha256(a),null,true,false,false,false,roads.map {road->
                SourceSegmentObservation(road.segmentId,road.name,road.status,road.role,road.insideSide,false,road.endpointAKind,road.endpointBKind,
                    "Positive control PDF page 1 explicitly depicts ${road.name}, its work instructions and complete endpoints. All three source roads are retained.",true)
            },emptyList(),UUID.randomUUID().toString(),null)
        return x.drafts.save(NativeAuthoringDraft(a,r,VerificationJurisdiction("Oakland County","Michigan","United States"),emptyList()),null)
    }
'''+s[end:]
p.write_text(s)
assert hashlib.sha256(p.read_bytes()).hexdigest()=='74ad500e61d86c5881fe443746074339756f1e294c6cadca3c48056acfa0ab6f'
print('Positive control: all three depicted roads retained')
