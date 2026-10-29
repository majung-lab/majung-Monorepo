package com.majung.vehicle

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.majung.vehicle.ui.home.HomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Figma 대조용 스냅샷. 화면 추가 시 SCREENS에 한 줄.
private val SCREENS: List<Pair<String, @Composable () -> Unit>> = listOf(
    "C01_home" to { HomeScreen(driving = false) },
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-mdpi")
class ScreenSnapshotTest {
    @Test
    fun snapshots() = SCREENS.forEach { (name, screen) ->
        captureRoboImage("build/outputs/roborazzi/$name.png") { screen() }
    }
}
