package th.ac.kmutnb.prachin.map.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeping floating controls clear of the panel at the bottom of a map screen.
 *
 * The panels here change height as the walker uses them - a route plan bar becomes a
 * navigation panel, a record button becomes a form with six rows in it - so the space a
 * floating button has to leave is not a number anyone can write down. Guessing it is how
 * a button ends up underneath a card, still drawn, still apparently tappable, and doing
 * nothing when pressed.
 *
 * So the panel reports its own height and the buttons sit above whatever it turned out to
 * be.
 */
@Composable
fun rememberOverlayHeight(): MutableState<Dp> = remember { mutableStateOf(0.dp) }

/** Reports this composable's measured height into [state], for others to lay out around. */
@Composable
fun Modifier.reportHeightTo(state: MutableState<Dp>): Modifier {
    val density = LocalDensity.current
    return onSizeChanged { size ->
        val height = with(density) { size.height.toDp() }
        if (height != state.value) state.value = height
    }
}

/**
 * How tall a card anchored to the bottom of a map screen may grow before it scrolls.
 *
 * A form that keeps growing eventually covers the map it is describing, and on a short
 * phone it pushes its own save button off the bottom of the screen. Past this it scrolls
 * instead, which keeps every control reachable and leaves the map visible.
 */
val BottomSheetCardMaxHeight: Dp = 340.dp
