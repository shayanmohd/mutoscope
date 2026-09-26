package com.mohdshayan.mutoscope.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens from BLUEPRINT.md section 7: light-table grey and pencil-test graphite, with
 * orchid as the one accent. Screens never use a hex value; they read MaterialTheme.colorScheme
 * or a named token below. Background tokens match the window background in colors.xml.
 */

// Light mode
val LightBackground = Color(0xFFECEEF3) // LightTable
val LightSurface = Color(0xFFF8F9FB) // Sheet
val LightOnBackground = Color(0xFF1C1D24) // Graphite
val LightOnSurfaceVariant = Color(0xFF575B68) // Lead
val LightOutline = Color(0xFF575B68) // Lead, control outlines
val LightAccent = Color(0xFF9825A7) // Orchid
val LightOnAccent = Color(0xFFFBF7FD) // OnOrchid
val LightAccentTint = Color(0xFFF0DDF2) // Orchid washed into Sheet, selected chips and rails
val LightError = Color(0xFFB3261E)

// Dark mode
val DarkBackground = Color(0xFF141319)
val DarkSurface = Color(0xFF1F1D26)
val DarkOnBackground = Color(0xFFEAE8F0)
val DarkOnSurfaceVariant = Color(0xFFA19DAE)
val DarkOutline = Color(0xFFA19DAE)
val DarkAccent = Color(0xFFDF8AEA)
val DarkOnAccent = Color(0xFF1E0A28)
val DarkAccentTint = Color(0xFF3A2640)
val DarkError = Color(0xFFF2B8B5)

/** Lead at 30 percent: hairlines and empty cells. */
const val HairlineAlpha = 0.3f

// Canvas-only tokens, never used for chrome.
val GhostBeforeLight = Color(0xFFC8412C)
val GhostBeforeDark = Color(0xFFE8705C)
val GhostAfterLight = Color(0xFF17857A)
val GhostAfterDark = Color(0xFF4FC7B6)
val DefaultPaper = Color(0xFFFCFCFA)

/** Paper colours offered for a new loop. They are the drawing's ground, not app chrome. */
val PaperChoices = listOf(
    Color(0xFFFCFCFA), // white
    Color(0xFFF3EFE4), // newsprint
    Color(0xFFE3E8EE), // blueprint grey
    Color(0xFFFBE9D0), // manila
    Color(0xFFDDEBDD), // sage card
    Color(0xFF2A2833), // black card
)

/** Spoken names for [PaperChoices], in the same order. */
val PaperNames = listOf("White", "Newsprint", "Blue grey", "Manila", "Sage", "Black card")

/** The 24 drawing swatches in the colour sheet. Drawing colours, not app chrome. */
val DrawingSwatches = listOf(
    Color(0xFF1C1D24), Color(0xFF4A4B57), Color(0xFF8B8D99), Color(0xFFC9CBD3),
    Color(0xFFF7F7F5), Color(0xFF5B3A29), Color(0xFF9C6644), Color(0xFFE0B48A),
    Color(0xFFD9482B), Color(0xFFE8703A), Color(0xFFF2C14E), Color(0xFFF6E27F),
    Color(0xFF8DBF4E), Color(0xFF3F8F4F), Color(0xFF1F6F5C), Color(0xFF4FC7B6),
    Color(0xFF3C8DDE), Color(0xFF2B4FA8), Color(0xFF232E6B), Color(0xFF7A5CC7),
    Color(0xFF9825A7), Color(0xFFDF8AEA), Color(0xFFE85D8A), Color(0xFFF4B6C2),
)
