package com.majung.vehicle

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.majung.vehicle.ui.destination.DestinationScreen
import com.majung.vehicle.ui.home.HomeScreen
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.ui.invite.InviteScreen
import com.majung.vehicle.ui.mode.ModeScreen
import com.majung.vehicle.ui.moving.FamilyStatus
import com.majung.vehicle.ui.moving.MovingScreen
import com.majung.vehicle.ui.place.PlaceScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Figma 대조용 스냅샷. 화면 추가 시 SCREENS에 한 줄.
private val SCREENS: List<Pair<String, @Composable () -> Unit>> = listOf(
    "C01_home" to { HomeScreen(driving = false) },
    "C02_destination" to { DestinationScreen(initialQuery = "스타필드") },
    "C03_mode" to { ModeScreen("스타필드 하남") },
    "C04_place" to { PlaceScreen("스타필드 하남", VisitMode.PICKUP) },
    "C05_invite" to { InviteScreen("스타필드 하남 · 북문 로비") },
    "C06_moving" to {
        MovingScreen("스타필드 하남 · 픽업하기", "북문 로비", 12, FamilyStatus("준비됐어요", "14:31"), onChangePlace = {})
    },
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
