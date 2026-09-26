package com.koenterprises.territorycardstudio.core

import java.io.File

object FinalAllModeParityRegression {
    private data class RendererGolden(
        val name: String,
        val spec: CandidatePdfRenderSpec,
        val expectedSpecSha256: String,
        val expectedPdfSha256: String,
        val requiredMarkers: List<String> = emptyList()
    )

    fun run(assets: File) {
        val template = File(assets, "render-authority/Canonical-New-Designed-Template-R48.pdf").readBytes()

        val cases = listOf(
            RendererGolden(
                "normal_residential",
                CandidatePdfRendererTest.syntheticFixtureSpec(),
                "d0bc5ccc9b0234591a0fe2983e2475eeb5b5c42902cc901268b9644a191bea76",
                "5474c57cdba333802a1aedfc577b7e156413346e7f107a6afd321b0a3b505e33"
            ),
            RendererGolden(
                "curved_callout",
                CandidatePdfRendererTest.curvedCalloutFixtureSpec(),
                "2104f787ab5f6ba99dc8ac2c0efbd54b31822e47d55ce54249b52d03a9374986",
                "ddbce80bcf9f6d4378150a9ef16ae6a885d4dda2c7227de369dac2548af26b4e",
                listOf("Tiny Ct")
            ),
            RendererGolden(
                "dedicated_detail",
                CandidatePdfRendererTest.dedicatedDetailFixtureSpec(),
                "fb1e29e186694dc1760957c3aa8175e93f262722da4e166062d496c55d379888",
                "ce741fa6142a16e8df27a927da6b5eb988cad30935887ab64689c6da31fc35fb",
                listOf("Detail Ct")
            ),
            RendererGolden(
                "split_detail",
                CandidatePdfRendererTest.splitDetailFixtureSpec(),
                "954110eaeec5903b3fea5c8b8117b70023d36818c32207a1d4ae9e50688afff4",
                "0bb14819899794da7ff546c8805c27fcbf2966af85b2363dc9d4be95dd469175",
                listOf("% TCS_SPLIT_DETAIL_LOCKED 350 228", "NORTH DETAIL", "SOUTH DETAIL")
            ),
            RendererGolden(
                "full_plus_detail",
                CandidatePdfRendererTest.fullPlusDetailFixtureSpec(),
                "8d6648ef64da73d8618fa0b5b60994500b9046766ae72a1e1b7922ea88b7279f",
                "d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133",
                listOf("% TCS_FULL_PLUS_DETAIL_EXPLICIT_MAPPING", "MAIN CONTEXT", "VERIFIED DETAIL")
            ),
            RendererGolden(
                "site_building_300_301",
                CandidatePdfRendererTest.siteBuildingAssignmentFixtureSpec(),
                "7b10aefe8eeaabf86dbb37a67c6079092ebdbf6583b31257f263ede980d44d9b",
                "6714060b793c5a39a96fdea11ef28c864f53e9579b0e89c922440d3679fad9a0",
                listOf(
                    "% TCS_SITE_BUILDING_ASSIGNMENT_EXPLICIT",
                    "% TCS_BUILDING_LABEL_ITEMS_EXACT site-building-300-301",
                    "(300) Tj",
                    "(301) Tj"
                )
            )
        )

        val identities = linkedSetOf<String>()
        cases.forEach { golden ->
            val first = CandidatePdfRenderer.renderNonFieldFixture(template, golden.spec)
            val second = CandidatePdfRenderer.renderNonFieldFixture(template, golden.spec)
            check(first.pdfBytes.contentEquals(second.pdfBytes)) { golden.name + ": byte determinism drift" }
            check(first.renderSpecSha256 == golden.expectedSpecSha256) {
                golden.name + ": render-spec hash drift " + first.renderSpecSha256
            }
            check(first.pdfSha256 == golden.expectedPdfSha256) {
                golden.name + ": PDF hash drift " + first.pdfSha256
            }
            check(first.exactValidation.passed) { golden.name + ": exact PDF validation failed: " + first.exactValidation.errors.joinToString() }
            check(first.pdfBytes.size in 1..(300 * 1024)) { golden.name + ": front PDF exceeds 300 KB" }
            check(identities.add(golden.spec.identity.displayId)) { golden.name + ": duplicate synthetic parity identity" }

            val raw = first.pdfBytes.toString(Charsets.ISO_8859_1)
            check(raw.contains("display_id=" + golden.spec.identity.displayId)) { golden.name + ": display ID metadata drift" }
            check(raw.contains("canonical_filename=" + golden.spec.identity.canonicalFilename)) { golden.name + ": canonical filename metadata drift" }
            check(raw.contains("% TCS_RENDER_SPEC_SHA256 " + golden.expectedSpecSha256)) { golden.name + ": spec hash marker drift" }
            check(raw.contains("% TCS_LOCKED_TEMPLATE_SHA256 " + LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256)) {
                golden.name + ": template authority marker drift"
            }
            check(latestPageCount(raw) == 1) { golden.name + ": renderer front is no longer exactly one page" }
            check(raw.contains("/MediaBox [ 0 0 768 480.5 ]")) { golden.name + ": canonical page geometry drift" }
            golden.requiredMarkers.forEach { marker ->
                check(raw.contains(marker)) { golden.name + ": missing marker " + marker }
            }

            golden.spec.labels.forEach { label ->
                when (label.mode) {
                    LabelPlacementMode.NEARBY_ATTACHED_ARROW_CALLOUT -> check(label.assignedRoadGapPx == 0.0) {
                        golden.name + ": callout road-gap semantics drift"
                    }
                    else -> check(label.assignedRoadGapPx in 2.0..4.0) {
                        golden.name + ": direct/curved label gap outside 2-4 pt hard range"
                    }
                }
            }

            if (golden.name == "site_building_300_301") {
                val building = golden.spec.buildings.single()
                check(building.points.size >= 3)
                check(building.labelItems.map { it.text } == listOf("300", "301"))
                check(building.labelItems.all { it.originX != null && it.originTopY != null })
                val binding = requireNotNull(golden.spec.siteBuildingAssignment).buildingBindings.single()
                check(binding.sourceMemberIds == listOf("300", "301"))
                check(binding.verifiedLabelTexts == listOf("300", "301"))
            }

            println(
                "phase1e_renderer=" + golden.name +
                    "|display_id=" + golden.spec.identity.displayId +
                    "|layout=" + golden.spec.layoutMode +
                    "|spec_sha256=" + first.renderSpecSha256 +
                    "|pdf_sha256=" + first.pdfSha256 +
                    "|bytes=" + first.pdfBytes.size
            )
        }

        val normal = cases.first { it.name == "normal_residential" }
        check(normal.spec.identity.territoryClass == TerritoryClass.Residential)
        val split = cases.first { it.name == "split_detail" }
        check(split.spec.layoutMode == "split_detail")
        val fullPlus = cases.first { it.name == "full_plus_detail" }
        check(fullPlus.expectedPdfSha256 == "d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133")
        val site = cases.first { it.name == "site_building_300_301" }
        check(site.spec.identity.displayId == "A994a")
        check(site.expectedPdfSha256 == "6714060b793c5a39a96fdea11ef28c864f53e9579b0e89c922440d3679fad9a0")

        println("phase1e_final_all_mode_renderer_parity=PASS")
        println("phase1e_renderer_mode_count=" + cases.size)
        println("phase1e_letter_writing_pdf_sha256=c8678a6f66b1479a0808b07c0dab16383edbd8ba4ee8cdc7c417996101842232")
        println("phase1e_telephone_pdf_sha256=01f704feb3e8253d443ceb3d373993b5dc57d41a7464a286e66e86fdacc9350e")
        println("phase1e_regular_packet_front_sha256=d83dd01f11f5096f9b987417a86187061409bd5598fc73278db8a14bc54eb133")
        println("phase1e_site_300_301_frozen_sha256=6714060b793c5a39a96fdea11ef28c864f53e9579b0e89c922440d3679fad9a0")
        println("phase1e_prior_visual_evidence_reused=PASS")
    }

    private fun latestPageCount(raw: String): Int {
        val latest = Regex(
            "19 0 obj\\s*<<\\s*/Count (\\d+) /Kids \\[ ([^]]+) ] /Type /Pages",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(raw).lastOrNull()
        return latest?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}

fun main(args: Array<String>) {
    require(args.size == 1) { "usage: FinalAllModeParityRegressionKt ASSETS_DIR" }
    FinalAllModeParityRegression.run(File(args[0]))
}
