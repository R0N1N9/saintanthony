package app.saintanthony.finder

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen to PNG files under build/shots, in light and dark, with sample data.
 * Run: ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest {
    // Location is granted before the app starts, so it opens past the welcome screen.
    @get:Rule(order = 0)
    val grant = object : ExternalResource() {
        override fun before() {
            shadowOf(ApplicationProvider.getApplicationContext<Application>())
                .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var m: AppModel

    @Before
    fun setUp() {
        rule.mainClock.autoAdvance = false // the AR trail animates forever
        m = ViewModelProvider(rule.activity)[AppModel::class.java]
    }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1200)
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun seed() {
        val here = Fix(37.7694, -122.4862, 6f, 20.0)
        m.location.pos = here
        m.orientation.heading = 20f
        if (m.items.isEmpty()) {
            val keys = m.save(Draft(null, "Keys", "key", "park bench"), LatLng(37.76995, -122.48565), 5f)
            m.save(Draft(null, "Bike", "bike", ""), LatLng(37.7702, -122.4883), 5f)
            m.save(Draft(null, "Headphones", "headphones", "desk drawer"), LatLng(37.76941, -122.48621), 5f)
            m.link(m.items[2].id, "ble:AA", "Pixel Buds Pro")
            m.selectTarget(keys.id)
        }
        val now = System.currentTimeMillis()
        m.signals.sources = listOf(
            Source("ble:AA", "Pixel Buds Pro", Kind.BLE, -58, now, paired = true),
            Source("ble:BB", "Pixel Watch 3", Kind.BLE, null, 0, paired = true),
            Source("ble:CC", "Kitchen speaker", Kind.BLE, -74, now),
            Source("ble:DD", "Tile Mate", Kind.BLE, -88, now),
            Source("wifi:EE", "Home Wi-Fi", Kind.WIFI, -52, now, connected = true),
            Source("wifi:FF", "Neighbour 5G", Kind.WIFI, -90, now),
        )
    }

    private fun tour(mode: ThemeMode, tag: String) {
        m.chooseTheme(mode)
        shot("$tag-1-empty")
        seed()
        shot("$tag-1-items")
        rule.onNodeWithText("Compass").performClick()
        shot("$tag-2-compass")
        rule.onNodeWithText("AR").performClick()
        shot("$tag-3-ar-item")
        rule.onNodeWithText("Signals").performClick()
        shot("$tag-4-signals")
        rule.onNodeWithText("Pixel Buds Pro").performClick()
        m.signals.rssi = -61f
        m.signals.lastHeard = System.currentTimeMillis()
        m.signals.trend = 3.4f
        m.signals.direction = Direction(48f, true)
        shot("$tag-5-tracking")
        m.orientation.heading = 44f // face the signal so the trail is in view
        rule.onNodeWithText("See in AR").performClick()
        shot("$tag-6-ar-signal")
        m.signals.direction = null
        m.signals.sweep = List(12) { it in 0..6 }
        shot("$tag-7-ar-sweep")
    }

    @Test fun light() = tour(ThemeMode.LIGHT, "light")

    @Test fun dark() = tour(ThemeMode.DARK, "dark")
}

/** The first-run screen, shown before location access has been granted. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
class WelcomeScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(mode: ThemeMode, tag: String) {
        ViewModelProvider(rule.activity)[AppModel::class.java].chooseTheme(mode)
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("build/shots/$tag-0-welcome.png")
    }

    @Test fun light() = shot(ThemeMode.LIGHT, "light")

    @Test fun dark() = shot(ThemeMode.DARK, "dark")
}
