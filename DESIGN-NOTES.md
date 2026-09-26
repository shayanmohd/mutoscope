# Mutoscope design notes

## Design read

Reading this as: a finger-first drawing tool for hobbyist and working animators, with a light-table studio language, leaning toward Anybody (a width-flexing grotesque) over Public Sans on the light-table grey and electric orchid palette.

## Dials

- DESIGN_VARIANCE 4. A tool must be predictable. The only asymmetry is the unequal reel strips: a 4-frame reel repeats against a 12-frame reel, and that rhythm is the picture.
- MOTION_INTENSITY 3. Motion answers actions. The canvas is the thing that moves. One orchestrated first-run moment (the sample's single play-through), everything else is sheets rising, a new cell growing, the marker sliding. Reduced motion snaps all of it and the play-through waits for Play.
- VISUAL_DENSITY 6. Canvas, strips and tools share a phone screen: rows are 48dp, cells 44 by 48dp, but the canvas always gets the most area.

## Tokens

| Token | Light | Dark | Role |
|---|---|---|---|
| LightTable | #ECEEF3 | #141319 | background |
| Sheet | #F8F9FB | #1F1D26 | sheets, strips, tiles |
| Graphite | #1C1D24 | #EAE8F0 | text, icons |
| Lead | #575B68 | #A19DAE | secondary text, outlines; 30 percent for hairlines and empty cells |
| Orchid | #9825A7 | #DF8AEA | the one accent |
| OnOrchid | #FBF7FD | #1E0A28 | text on orchid |

Canvas-only: GhostBefore #C8412C / #E8705C, GhostAfter #17857A / #4FC7B6, default paper #FCFCFA.

Type: Anybody variable (display, weight 750 width 125 for the Projects title and empty-state headlines, 650 width 100 for sheet titles). Public Sans 400, 500, 600 for text; tabular figures on every counter.

Radius: 4dp cells, chips, swatches. 10dp buttons, fields, canvas frame. 20dp sheet tops. The play button is the one circle.

## The one memorable thing

The cycle marker: under the canvas every reel is a strip of numbered cells repeated to fill the width, and one orchid rule crosses every strip where all reels realign, labelled "Cycle: 12 frames". Everything else stays quiet so that rule is the loudest mark on the screen after the drawing itself.

## Plan review against the brief

- Default I would reach for: a dark canvas app with neon accent and a timeline of equal-width frame thumbnails. Rejected: the light table is grey and daylight, and the strips are numbered cells, not thumbnails, so a short reel is visibly short.
- Default card grid on Projects: kept a grid (it is a gallery of loops) but tiles are the loop's own first frame on its paper, with name and cycle text below, no shadow cards.
- Tools: six equal buttons would be a template row; the active tool carries the orchid fill and its glyph changes with the brush, so the row reads as state, not decoration.
