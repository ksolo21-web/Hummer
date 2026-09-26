package com.koenterprises.territorycardstudio.core

import java.io.File
import java.security.MessageDigest

fun main(args: Array<String>) {
    require(args.size == 3) { "usage: CanonicalFrontBackFixtureGenerator ASSETS_DIR FRONT.pdf COMBINED.pdf" }
    val assets = File(args[0])
    val frontOutput = File(args[1])
    val combinedOutput = File(args[2])
    val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()
    val frontSpec = CandidatePdfRendererTest.fullPlusDetailFixtureSpec()
    val front = CandidatePdfRenderer.renderNonFieldFixture(template, frontSpec)
    frontOutput.parentFile?.mkdirs()
    frontOutput.writeBytes(front.pdfBytes)
    val entries = listOf(
        "101 Example Way Apt 1", "102 Example Way Apt 2", "103 Example Way Apt 3", "104 Example Way Apt 4",
        "105 Example Way Apt 5", "106 Example Way Apt 6", "107 Example Way Apt 7", "108 Example Way Apt 8",
        "201 Sample Court Unit A", "201 Sample Court Unit B", "203 Sample Court Unit A", "203 Sample Court Unit B",
        "310 Demo Drive Apt 10", "310 Demo Drive Apt 11", "312 Demo Drive Apt 20", "312 Demo Drive Apt 21",
        "400 Test Lane Unit 1", "400 Test Lane Unit 2", "402 Test Lane Unit 3", "402 Test Lane Unit 4"
    )
    val inventory = entries.mapIndexed { index, address -> "fixture-${index + 1}|$address" }.joinToString("\n")
    val sourceSha = MessageDigest.getInstance("SHA-256").digest(inventory.toByteArray()).joinToString("") { "%02x".format(it) }
    val back = CanonicalAddressBackSpec(
        identity = frontSpec.identity,
        locality = frontSpec.locality,
        updated = frontSpec.updated,
        sourceInventorySha256 = sourceSha,
        sourceInventoryVerified = true,
        entries = entries.mapIndexed { index, address -> CanonicalAddressEntry("fixture-${index + 1}", address) },
        nonFieldFixture = true
    )
    val result = CanonicalFrontBackPdfAssembler.assembleNonFieldFixture(front.pdfBytes, back)
    combinedOutput.parentFile?.mkdirs()
    combinedOutput.writeBytes(result.pdfBytes)
    println("front_pdf=${frontOutput.absolutePath}")
    println("front_pdf_sha256=${front.pdfSha256}")
    println("combined_pdf=${combinedOutput.absolutePath}")
    println("combined_pdf_sha256=${result.pdfSha256}")
    println("back_spec_sha256=${result.backSpecSha256}")
    println("address_source_sha256=$sourceSha")
    println("page_count=${result.validation.pageCount}")
    println("page_roles=${result.validation.pageRoles.joinToString(",")}")
    println("validation_passed=${result.validation.passed}")
}
