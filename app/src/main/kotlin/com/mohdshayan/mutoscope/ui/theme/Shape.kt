package com.mohdshayan.mutoscope.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   RadiusSm  4dp: reel cells, chips, swatches
 *   RadiusMd 10dp: buttons, fields, the canvas frame, tiles
 *   RadiusLg 20dp: sheet tops and dialogs
 * The play button is the one circle.
 */
val RadiusSm = 4.dp
val RadiusMd = 10.dp
val RadiusLg = 20.dp

val CellShape = RoundedCornerShape(RadiusSm)
val ControlShape = RoundedCornerShape(RadiusMd)
val SheetShape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusMd),
    extraLarge = RoundedCornerShape(RadiusLg),
)
