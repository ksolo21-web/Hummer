from pathlib import Path

path = Path('MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/update/ContentPayloadDecoderTest.kt')
text = path.read_text(encoding='utf-8')
weekly = 'https://www.jw.org/finder?wtlocale=E&docid=202026244&srcid=share'
family = 'https://www.jw.org/finder?wtlocale=E&pub=nwtsty&bible=51003012-51003013&srcid=share'
family_section = 'https://www.jw.org/finder?wtlocale=E&pub=nwtsty&bible=51003014&srcid=share'
text = text.replace('https://www.jw.org/en/library/jw-meeting-workbook/', weekly)
text = text.replace('https://www.jw.org/en/bible-teachings/family/', family)
text = text.replace('https://wol.jw.org/en/wol/h/r1/lp-e', family_section)
anchor = '''    private fun signedShape(
'''
extra = '''    @Test
    fun rejectsSignedSpiritualLinksThatCannotOpenExactJwLibraryMaterial() {
        val payload = """
            {
              "weekId":"2026-07-27",
              "weekLabel":"July 27–August 2, 2026",
              "bibleReading":"Jeremiah 17–18",
              "officialUrl":"https://www.jw.org/en/library/jw-meeting-workbook/",
              "parts":[{
                "id":"treasures",
                "title":"Treasures From God's Word",
                "subtitle":"Prepare the assigned material",
                "detail":"Use the official workbook and Bible context to prepare this section carefully.",
                "totalUnits":3,
                "officialUrl":"https://www.jw.org/en/library/jw-meeting-workbook/",
                "orderIndex":0
              }]
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val update = signedShape(
            type = ContentType.WEEKLY_MEETING_STUDY,
            id = "weekly:2026-07-27",
            payload = payload,
            sources = listOf("https://www.jw.org/en/library/jw-meeting-workbook/"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            decoder.decode(update)
        }
    }

'''
if text.count(anchor) != 1:
    raise SystemExit('Expected one signedShape anchor.')
text = text.replace(anchor, extra + anchor, 1)
path.write_text(text, encoding='utf-8')
print('Updated signed-content tests for exact JW Library target contract.')
