package com.koenterprises.territorycardstudio.core

import java.io.File

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: CanonicalFrontBackOverflowRegressionKt <territory-assets-dir>" }
    val assets = File(args[0])
    val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    val frontSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec()
    val front = CandidatePdfRenderer.renderNonFieldFixture(template, frontSpec)
    val entries = (1..241).map { n ->
        CanonicalAddressEntry(
            sourceId = "overflow-" + n,
            addressLine = n.toString() + " Overflow Blvd Unit " + n
        )
    }
    val back = CanonicalAddressBackSpec(
        identity = frontSpec.identity,
        locality = frontSpec.locality,
        updated = frontSpec.updated,
        sourceInventorySha256 = "b".repeat(64),
        sourceInventoryVerified = true,
        entries = entries,
        nonFieldFixture = true
    )
    val failed = runCatching {
        CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
    }.isFailure
    check(failed) { "Back-page overflow unexpectedly passed canonical front/back assembly" }
    println("phase1d_back_overflow_fail_closed=PASS")
    println("phase1d_back_overflow_entry_count=241")
}
