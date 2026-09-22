package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the drawn dot is allowed to sit on the route line.
 *
 * Worth pinning because the failure is silent and one-directional: snap too eagerly and
 * the app tells somebody in a forest that they are on a path they have already left, and
 * it will look completely normal while it does it.
 */
class RouteSnapperTest {

    private fun snapper() = RouteSnapper()

    @Test
    fun `a fix beside the path is drawn on it`() {
        // The everyday case: a 1.5 m fix landing in the verge of a 3 m path.
        assertTrue(snapper().onDistanceFromRoute(2.0))
    }

    @Test
    fun `a walker who has genuinely left the route is not put back`() {
        assertFalse(snapper().onDistanceFromRoute(30.0))
    }

    @Test
    fun `snapping lets go well before the off-route warning fires`() {
        // If it held on past this, the dot would still be on the line at the moment the
        // app started saying the walker was off it - two answers, both on screen.
        assertTrue(RouteSnapper.DEFAULT_EXIT_M < NavigationEngine.DEFAULT_OFF_ROUTE_THRESHOLD_M)
    }

    @Test
    fun `standing at the threshold does not make the dot flicker`() {
        val snapper = snapper()
        snapper.onDistanceFromRoute(5.0)

        // Drifting a little past the entry distance keeps the dot where it was, instead of
        // throwing it between the line and the grass on every fix.
        assertTrue(snapper.onDistanceFromRoute(RouteSnapper.DEFAULT_ENTER_M + 1.0))
        assertTrue(snapper.onDistanceFromRoute(RouteSnapper.DEFAULT_ENTER_M + 3.0))
    }

    @Test
    fun `walking clearly away lets the dot go, and coming back needs the closer distance`() {
        val snapper = snapper()
        snapper.onDistanceFromRoute(2.0)

        assertFalse(snapper.onDistanceFromRoute(RouteSnapper.DEFAULT_EXIT_M + 1.0))
        // Back inside the wider band is not enough to be helped again - it has to be the
        // narrow one, or the dot would latch on from further out than it ever let go.
        assertFalse(snapper.onDistanceFromRoute(RouteSnapper.DEFAULT_EXIT_M - 1.0))
        assertTrue(snapper.onDistanceFromRoute(RouteSnapper.DEFAULT_ENTER_M - 1.0))
    }

    @Test
    fun `reset stops helping`() {
        val snapper = snapper()
        snapper.onDistanceFromRoute(1.0)
        assertTrue(snapper.isSnapped)

        snapper.reset()

        assertFalse(snapper.isSnapped)
    }

    @Test
    fun `the entry distance is well inside the off-route threshold`() {
        // The gap between them is the room a walker has to step around a puddle without
        // being told they are lost, and without being drawn somewhere they are not.
        assertTrue(RouteSnapper.DEFAULT_ENTER_M < RouteSnapper.DEFAULT_EXIT_M)
        assertTrue(RouteSnapper.DEFAULT_ENTER_M * 2 <= NavigationEngine.DEFAULT_OFF_ROUTE_THRESHOLD_M)
    }
}
