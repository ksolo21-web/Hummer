# My Study Companion 0.14.1 — Real Workbook Art Acceptance

The workbook engine must create actual activity pages. Activity labels, empty boxes, prompt-only cards and palette lists are not acceptable substitutes.

## Drawing pages

- Every drawing activity renders recognizable vector line art selected for the lesson.
- The line art remains visible while the user draws with touch or stylus.
- The page includes functional undo, clear and stroke controls.
- User strokes persist with workbook progress.
- Blank and completed PDF exports include the same guided artwork; completed exports also include saved user strokes.

## Color-by-number pages

- Every color-by-number activity contains closed numbered regions, not a list of color names.
- A fixed visible key maps each number to a color.
- Tapping a region fills that region with its assigned color.
- Progress persists independently for every region and workbook page.
- Printable/PDF output includes the numbered artwork, and completed output includes the selected fills.

## Required surfaces

The same activity identity and saved progress must work in:

- Android phone
- Fold/tablet layouts
- PWA/web
- printable PDF output

## Rejection conditions

Reject the build if any drawing or color-by-number activity falls back to:

- a blank rectangle
- a caption field without artwork
- a generic shared scribble box
- color labels or swatches without numbered regions
- artwork visible only on one platform
- printable output that omits the generated activity art

## Current deterministic markers

The reconstructed source gate requires Android markers `drawWorkbookArt`, `drawPdfWorkbookArt`, `detectTapGestures`, and `Guided drawing canvas`, plus PWA markers `renderColorByNumber`, `drawArtCanvas`, `artSvg`, and `svgArtStrokes`.
