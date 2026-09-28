package com.koenterprises.territorycardstudio.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant

/** Local human reconciliation, not a signature, GIS decision, or PDF approval. */
data class SourceSegmentObservation(
    val segmentId: String, val name: String, val status: String, val role: String,
    val insideSide: String, val accessOnly: Boolean, val endpointAKind: String,
    val endpointBKind: String, val evidenceNote: String, val confirmed: Boolean
)
data class SourceBuildingObservation(val buildingId: String, val sourceMembers: List<String>,
    val assigned: Boolean, val evidenceNote: String, val confirmed: Boolean,
    val supplementalReference: SupplementalBuildingReference? = null)

data class NativeSourceReconciliation(
    val territory: String, val mode: String, val knowledgeBaseRevision: String,
    val importedSourceSha256: String, val lockedReferenceSha256: String,
    val sourceClass: String, val author: String, val reviewedAtUtc: String,
    val assignmentContentSha256: String, val inventorySha256: String?,
    val sourceCoverageComplete: Boolean, val explicitAssignmentConfirmation: Boolean,
    val crossTerritoryInferenceUsed: Boolean, val styleOnlyGeographyUsed: Boolean,
    val segments: List<SourceSegmentObservation>, val buildings: List<SourceBuildingObservation>,
    val registrationId: String, val predecessorEventSha256: String?, val imageInterpretationSha256:String?=null
)

data class NativeReconciliationAssessment(val truth: CandidateSourceTruthState, val failures: List<String>) {
    val passed: Boolean get() = failures.isEmpty()
}

/**
 * Shared, closed v1 exchange schema. This evaluates an explicit observation against a candidate;
 * it cannot prove that the author read the source correctly. Native review must display the exact
 * source and proposed facts before recording explicitAssignmentConfirmation.
 */
object NativeSourceReconciliationContract {
    const val SCHEMA = "native-source-reconciliation-v2"
    const val MAX_BYTES = 1024 * 1024
    private val hash = Regex("[0-9a-f]{64}")
    private val modes = setOf("REGULAR", "LETTER_WRITING", "TELEPHONE")
    private val work = setOf("yellow", "green", "red", "context")
    private val roles = setOf("perimeter", "interior", "excluded", "context")
    private val ends = setOf("junction", "termination")

    /** Length-prefixed UTF-8 / big-endian primitives; preserves every typed field and order.
     * Authority digest is the sole exclusion, preventing a circular receipt hash.
     * Native/Python consumers implement this explicitly rather than positional reflection. */
    fun assignmentContentSha256(a: CurrentAuthoritativeAssignmentState): String {
        val bytes = java.io.ByteArrayOutputStream()
        val out = java.io.DataOutputStream(bytes)
        fun text(v: String) { val b = v.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
        fun number(v: Double) { require(v.isFinite()); out.writeDouble(v) }
        fun point(v: Point2D) { number(v.x); number(v.y) }
        fun <T> list(v: List<T>, write: (T) -> Unit) { out.writeInt(v.size); v.forEach(write) }
        text("native-assignment-content-v1")
        text(a.displayId); out.writeInt(a.identity.baseNumber); text(a.identity.territoryClass.token)
        text(a.identity.suffix?.toString() ?: ""); text(a.canonicalFilename); text(a.knowledgeBaseRevision)
        text(a.authorityRole); text(a.sourceMasterLabel); text(a.locality); text(a.updated)
        list(a.directionsLines, ::text); text(a.layoutMode); text(a.housingType); text(a.coordinateSpace.name)
        list(a.roads) { r -> text(r.segmentId); text(r.name); text(r.normalizedName); text(r.status); text(r.role)
            text(r.insideSide); out.writeBoolean(r.accessOnly); text(r.endpointAKind); text(r.endpointBKind)
            number(r.widthPt); list(r.points, ::point) }
        list(a.buildings) { b -> text(b.buildingId); text(b.label); text(b.housingType); out.writeBoolean(b.assigned)
            text(b.attachedGroup); list(b.sourceMembers, ::text)
            list(b.labelItems) { i -> text(i.text); point(i.center); out.writeBoolean(i.origin != null)
                i.origin?.let(::point); number(i.angleDeg); number(i.fontSizePt) }
            list(b.polygon, ::point) }
        out.flush()
        return BundleIntegrity.sha256(bytes.toByteArray().inputStream())
    }

    /** Separates stable user observations from registration execution metadata.
     * Inventory is bound independently; its authority digest is assigned after registration. */
    fun draftFactsSha256(r: NativeSourceReconciliation): String = BundleIntegrity.sha256(encode(r.copy(
        reviewedAtUtc="2000-01-01T00:00:00Z", registrationId="00000000-0000-0000-0000-000000000000",
        predecessorEventSha256=null, explicitAssignmentConfirmation=false, inventorySha256=null)).inputStream())

    fun assess(kb: TerritoryKnowledgeBase, r: NativeSourceReconciliation,
        a: CurrentAuthoritativeAssignmentState, mode: String, sourceSha256: String,
        inventorySha256: String?): NativeReconciliationAssessment {
        validate(r)
        val errors = mutableListOf<String>()
        val slot = kb.assignments[r.territory]
        if (slot == null) errors += "UNKNOWN_TERRITORY"
        if (slot != null) {
            if (!slot.needsNewCard) errors += "APPROVED_ARTIFACT_LOCKED"
            if (r.lockedReferenceSha256 != slot.referenceSha256) errors += "REFERENCE_MISMATCH"
            // A legacy hash cannot be promoted just by attaching a locally authored statement.
            if (r.importedSourceSha256 == slot.referenceSha256)
                errors += "LEGACY_SOURCE_NOT_CURRENT"
            if (a.identity != slot.identity || a.canonicalFilename != slot.canonicalFilename) errors += "IDENTITY_MISMATCH"
            if (a.housingType != slot.housingType) errors += "HOUSING_MISMATCH"
            val telephone = slot.identity.territoryClass in setOf(TerritoryClass.Telephone, TerritoryClass.TelephoneApartment)
            if ((telephone && mode != "TELEPHONE") || (!telephone && mode !in setOf("REGULAR", "LETTER_WRITING"))) errors += "CLASS_MODE_MISMATCH"
        }
        if (r.mode != mode || (mode == "REGULAR" && inventorySha256 != null) ||
            (mode != "REGULAR" && inventorySha256 == null)) errors += "MODE_MISMATCH"
        if (r.territory != a.displayId) errors += "TERRITORY_MISMATCH"
        if (r.knowledgeBaseRevision != kb.revision || a.knowledgeBaseRevision != kb.revision) errors += "REVISION_MISMATCH"
        if (r.importedSourceSha256 != sourceSha256) errors += "SOURCE_MISMATCH"
        if (r.assignmentContentSha256 != assignmentContentSha256(a)) errors += "ASSIGNMENT_MISMATCH"
        if (r.inventorySha256 != inventorySha256) errors += "INVENTORY_MISMATCH"
        if (r.sourceClass != "current_assignment_map") errors += "SOURCE_CLASS_NOT_CURRENT"
        if (!r.sourceCoverageComplete) errors += "SOURCE_COVERAGE_UNCONFIRMED"
        if (!r.explicitAssignmentConfirmation) errors += "EXPLICIT_CONFIRMATION_REQUIRED"
        if (r.segments.any { !it.confirmed || it.evidenceNote.isBlank() }) errors += "SEGMENT_REVIEW_INCOMPLETE"
        if (r.buildings.any { !it.confirmed || it.evidenceNote.isBlank() }) errors += "BUILDING_REVIEW_INCOMPLETE"
        val expected = r.segments.associateBy { it.segmentId }
        val actual = a.roads.associateBy { it.segmentId }
        val missing = (expected.keys - actual.keys).size
        val unexpected = (actual.keys - expected.keys).size
        val conflicts = expected.count { (id, s) -> actual[id]?.let { !same(s, it) } ?: false }
        val perimeter = a.roads.count { road -> road.role == "perimeter" &&
            (road.insideSide !in setOf("left", "right") || expected[road.segmentId]?.let { !it.confirmed || !same(it, road) } != false) }
        val observedBuildings = r.buildings.associateBy { it.buildingId }
        val actualBuildings = a.buildings.associateBy { it.buildingId }
        val unresolvedBuildings = (observedBuildings.keys union actualBuildings.keys).count { id ->
            val s = observedBuildings[id]; val b = actualBuildings[id]
            s == null || b == null || !s.confirmed || s.assigned != b.assigned || s.sourceMembers.sorted() != b.sourceMembers.sorted() ||
                s.supplementalReference?.let { SupplementalBuildingReferenceContract.failures(it,b,sourceSha256,r.lockedReferenceSha256).isNotEmpty() } == true
        }
        r.buildings.forEach { observation -> observation.supplementalReference?.let { reference ->
            actualBuildings[observation.buildingId]?.let { building ->
                errors += SupplementalBuildingReferenceContract.failures(reference,building,sourceSha256,r.lockedReferenceSha256)
            }
        } }
        val color = TopologyOverlapDecisionEngine.validateIntersectionColorRoles(a.roads)
        if (!color.passed) errors += "TOPOLOGY_COLOR_INVALID"
        val truth = CandidateSourceTruthState(a.displayId, a.authoritySha256,
            errors.isEmpty() && missing == 0 && unexpected == 0 && conflicts == 0 && perimeter == 0 && unresolvedBuildings == 0,
            missing, unexpected, conflicts, perimeter, color.midSegmentColorTransitionCount,
            unresolvedBuildings, r.crossTerritoryInferenceUsed, r.styleOnlyGeographyUsed)
        errors += truth.hardFailures(a.displayId)
        return NativeReconciliationAssessment(truth, errors.distinct())
    }

    private fun same(s: SourceSegmentObservation, r: RoadGeometry) =
        s.name == r.name && s.status == r.status && s.role == r.role && s.insideSide == r.insideSide &&
            s.accessOnly == r.accessOnly && s.endpointAKind == r.endpointAKind && s.endpointBKind == r.endpointBKind

    fun validate(r: NativeSourceReconciliation) {
        fun text(s: String, n: Int = 256, blank: Boolean = false) {
            require((blank || s.isNotBlank()) && s == s.trim() && s.length <= n && s.all { it.code in 32..126 }) { "Invalid reconciliation text" }
        }
        text(r.territory, 16); TerritoryIdentity.parse(r.territory)
        require(r.registrationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        r.predecessorEventSha256?.let { require(hash.matches(it)) }
        require(r.mode in modes); text(r.knowledgeBaseRevision)
        listOf(r.importedSourceSha256, r.lockedReferenceSha256, r.assignmentContentSha256).forEach { require(hash.matches(it)) }
        r.inventorySha256?.let { require(hash.matches(it)) }
        require(r.sourceClass in setOf("current_assignment_map", "legacy_reference", "style_only"))
        require(r.imageInterpretationSha256==null || r.imageInterpretationSha256.matches(Regex("[0-9a-f]{64}")))
        text(r.author, 120); require(r.reviewedAtUtc.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")))
        require(!r.reviewedAtUtc.startsWith("0000") && Instant.parse(r.reviewedAtUtc).toString() == r.reviewedAtUtc)
        require(r.segments.size in 1..512 && r.buildings.size <= 512)
        require(r.segments.map { it.segmentId }.distinct().size == r.segments.size)
        require(r.buildings.map { it.buildingId }.distinct().size == r.buildings.size)
        r.segments.forEach {
            text(it.segmentId, 120); text(it.name); text(it.evidenceNote, 1000, true)
            require(it.status in work && it.role in roles && it.insideSide in setOf("", "left", "right"))
            require(it.endpointAKind in ends && it.endpointBKind in ends)
        }
        r.buildings.forEach {
            text(it.buildingId, 120); text(it.evidenceNote, 1000, true)
            it.supplementalReference?.let(SupplementalBuildingReferenceContract::validate)
            require(it.sourceMembers.size in (if (it.assigned) 1 else 0)..512 && it.sourceMembers.distinct().size == it.sourceMembers.size)
            it.sourceMembers.forEach { member -> text(member, 120) }
        }
    }

    fun encode(r: NativeSourceReconciliation): ByteArray {
        validate(r)
        fun s(v: String) = JsonValue.Str(v)
        fun b(v: Boolean) = JsonValue.Bool(v)
        fun obj(vararg values: Pair<String, JsonValue>) = JsonValue.Obj(linkedMapOf(*values))
        val root = obj("imageInterpretationSha256" to (r.imageInterpretationSha256?.let(::s) ?: JsonValue.Null), "schema" to s(SCHEMA), "registrationId" to s(r.registrationId), "predecessorEventSha256" to (r.predecessorEventSha256?.let(::s) ?: JsonValue.Null), "territory" to s(r.territory), "mode" to s(r.mode),
            "knowledgeBaseRevision" to s(r.knowledgeBaseRevision), "importedSourceSha256" to s(r.importedSourceSha256),
            "lockedReferenceSha256" to s(r.lockedReferenceSha256), "sourceClass" to s(r.sourceClass),
            "author" to s(r.author), "reviewedAtUtc" to s(r.reviewedAtUtc), "assignmentContentSha256" to s(r.assignmentContentSha256),
            "inventorySha256" to (r.inventorySha256?.let(::s) ?: JsonValue.Null),
            "sourceCoverageComplete" to b(r.sourceCoverageComplete), "explicitAssignmentConfirmation" to b(r.explicitAssignmentConfirmation),
            "crossTerritoryInferenceUsed" to b(r.crossTerritoryInferenceUsed), "styleOnlyGeographyUsed" to b(r.styleOnlyGeographyUsed),
            "segments" to JsonValue.Arr(r.segments.map { obj("segmentId" to s(it.segmentId), "name" to s(it.name),
                "status" to s(it.status), "role" to s(it.role), "insideSide" to s(it.insideSide), "accessOnly" to b(it.accessOnly),
                "endpointAKind" to s(it.endpointAKind), "endpointBKind" to s(it.endpointBKind), "evidenceNote" to s(it.evidenceNote), "confirmed" to b(it.confirmed)) }),
            "buildings" to JsonValue.Arr(r.buildings.map { obj("buildingId" to s(it.buildingId),
                "sourceMembers" to JsonValue.Arr(it.sourceMembers.map(::s)), "assigned" to b(it.assigned),
                "evidenceNote" to s(it.evidenceNote), "confirmed" to b(it.confirmed)).let { building ->
                    it.supplementalReference?.let { ref -> JsonValue.Obj(LinkedHashMap(building.values + ("supplementalReference" to obj(
                        "referenceSha256" to s(ref.referenceSha256), "currentSourceSha256" to s(ref.currentSourceSha256),
                        "buildingContentSha256" to s(ref.buildingContentSha256), "referenceLocation" to s(ref.referenceLocation),
                        "correspondenceEvidence" to s(ref.correspondenceEvidence), "confirmed" to b(ref.confirmed))))) } ?: building
                } }))
        return canonical(root).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): NativeSourceReconciliation {
        require(bytes.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth = 0; var quote = false; var escape = false
        text.forEach { c -> if (quote) { if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') quote = false }
            else when(c) { '"' -> quote = true; '[', '{' -> { depth++; require(depth <= 8) }; ']', '}' -> { depth--; require(depth >= 0) } } }
        require(!quote && depth == 0)
        val o = StrictJson.parse(text.reader()).obj("reconciliation")
        fun Map<String, JsonValue>.str(k: String) = getValue(k).string(k)
        fun Map<String, JsonValue>.bool(k: String) = getValue(k).bool(k)
        fun keys(v: Map<String, JsonValue>, vararg expected: String) { require(v.keys == expected.toSet()) { "Reconciliation schema drift" } }
        keys(o, "imageInterpretationSha256", "schema", "registrationId", "predecessorEventSha256", "territory", "mode", "knowledgeBaseRevision", "importedSourceSha256", "lockedReferenceSha256", "sourceClass", "author", "reviewedAtUtc", "assignmentContentSha256", "inventorySha256", "sourceCoverageComplete", "explicitAssignmentConfirmation", "crossTerritoryInferenceUsed", "styleOnlyGeographyUsed", "segments", "buildings")
        require(o.str("schema") == SCHEMA)
        val roads = o.getValue("segments").arr("segments").map { v -> val x = v.obj("segment")
            keys(x, "segmentId", "name", "status", "role", "insideSide", "accessOnly", "endpointAKind", "endpointBKind", "evidenceNote", "confirmed")
            SourceSegmentObservation(x.str("segmentId"), x.str("name"), x.str("status"), x.str("role"), x.str("insideSide"), x.bool("accessOnly"), x.str("endpointAKind"), x.str("endpointBKind"), x.str("evidenceNote"), x.bool("confirmed")) }
        val buildings = o.getValue("buildings").arr("buildings").map { v -> val x = v.obj("building")
            keys(x.filterKeys { it != "supplementalReference" }, "buildingId", "sourceMembers", "assigned", "evidenceNote", "confirmed")
            val supplemental=x["supplementalReference"]?.obj("supplemental reference")?.let { ref ->
                keys(ref,"referenceSha256","currentSourceSha256","buildingContentSha256","referenceLocation","correspondenceEvidence","confirmed")
                SupplementalBuildingReference(ref.str("referenceSha256"),ref.str("currentSourceSha256"),ref.str("buildingContentSha256"),ref.str("referenceLocation"),ref.str("correspondenceEvidence"),ref.bool("confirmed"))
            }
            SourceBuildingObservation(x.str("buildingId"), x.getValue("sourceMembers").arr("members").map { it.string("member") }, x.bool("assigned"), x.str("evidenceNote"), x.bool("confirmed"), supplemental) }
        val r = NativeSourceReconciliation(o.str("territory"), o.str("mode"), o.str("knowledgeBaseRevision"), o.str("importedSourceSha256"), o.str("lockedReferenceSha256"), o.str("sourceClass"), o.str("author"), o.str("reviewedAtUtc"), o.str("assignmentContentSha256"), if (o["inventorySha256"] == JsonValue.Null) null else o.str("inventorySha256"), o.bool("sourceCoverageComplete"), o.bool("explicitAssignmentConfirmation"), o.bool("crossTerritoryInferenceUsed"), o.bool("styleOnlyGeographyUsed"), roads, buildings, o.str("registrationId"), if (o["predecessorEventSha256"] == JsonValue.Null) null else o.str("predecessorEventSha256"), if(o["imageInterpretationSha256"]==JsonValue.Null)null else o.str("imageInterpretationSha256"))
        require(encode(r).contentEquals(bytes)) { "Use canonical reconciliation encoding" }
        return r
    }

    private fun canonical(v: JsonValue): String = when(v) {
        is JsonValue.Obj -> v.values.toSortedMap().entries.joinToString(",", "{", "}") { canonical(JsonValue.Str(it.key)) + ":" + canonical(it.value) }
        is JsonValue.Arr -> v.values.joinToString(",", "[", "]", transform = ::canonical)
        is JsonValue.Str -> "\"" + v.value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is JsonValue.Bool -> v.value.toString()
        JsonValue.Null -> "null"
        is JsonValue.Num -> error("Numbers are not part of the reconciliation v1 schema")
    }
}
