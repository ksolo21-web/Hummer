package com.koenterprises.territorycardstudio.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SupplementalBuildingReferenceTest {
    private val source="a".repeat(64)
    private val reference="b".repeat(64)
    private val building=BuildingGeometry("current-1","500/502","apartment",true,"",listOf("500","502"),emptyList(),
        listOf(Point2D(220.0,90.0),Point2D(250.0,90.0),Point2D(250.0,130.0),Point2D(220.0,130.0)))
    private fun match(confirmed:Boolean=false)=SupplementalBuildingReference(reference,source,
        SupplementalBuildingReferenceContract.contentSha256(building),"Page 1 north building",
        "Same L-shaped footprint west of the current entrance road",confirmed)
    @Test fun referenceStartsPendingAndCannotReplaceCurrentSource(){
        assertEquals(listOf("SUPPLEMENTAL_MATCH_UNCONFIRMED"),SupplementalBuildingReferenceContract.failures(match(),building,source,reference))
        assertThrows(IllegalArgumentException::class.java){SupplementalBuildingReferenceContract.validate(match().copy(currentSourceSha256=reference))}
    }
    @Test fun explicitMatchIsBoundToBothSourcesAndAllBuildingFacts(){
        val r=match(true)
        assertTrue(SupplementalBuildingReferenceContract.failures(r,building,source,reference).isEmpty())
        listOf(building.copy(assigned=false),building.copy(sourceMembers=listOf("500","504")),
            building.copy(polygon=building.polygon.map {it.copy(x=it.x+1)}),building.copy(buildingId="other"),
            building.copy(label="different")).forEach { changed ->
            assertTrue("SUPPLEMENTAL_BUILDING_CHANGED" in SupplementalBuildingReferenceContract.failures(r,changed,source,reference))
        }
        assertTrue("SUPPLEMENTAL_CURRENT_SOURCE_CHANGED" in SupplementalBuildingReferenceContract.failures(r,building,"c".repeat(64),reference))
        assertTrue("SUPPLEMENTAL_REFERENCE_MISMATCH" in SupplementalBuildingReferenceContract.failures(r,building,source,"c".repeat(64)))
    }
    @Test fun emptyOrGenericEvidenceCannotBeRecorded(){
        assertThrows(IllegalArgumentException::class.java){SupplementalBuildingReferenceContract.validate(match().copy(correspondenceEvidence=""))}
        assertThrows(IllegalArgumentException::class.java){SupplementalBuildingReferenceContract.validate(match().copy(referenceLocation="page"))}
    }
}
