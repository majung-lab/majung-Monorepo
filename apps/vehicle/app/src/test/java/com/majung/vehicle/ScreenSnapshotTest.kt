package com.majung.vehicle

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.majung.vehicle.ui.destination.DestinationScreen
import com.majung.vehicle.ui.history.HistoryScreen
import com.majung.vehicle.ui.home.HomeScreen
import com.majung.vehicle.domain.model.VisitMode
import com.majung.vehicle.ui.arrival.DropoffArrivalScreen
import com.majung.vehicle.ui.arrival.ParkArrivalScreen
import com.majung.vehicle.ui.arrival.PickupArrivalScreen
import com.majung.vehicle.ui.invite.InviteScreen
import com.majung.vehicle.ui.mode.ModeScreen
import com.majung.vehicle.ui.moving.FamilyStatus
import com.majung.vehicle.ui.moving.MovingScreen
import com.majung.vehicle.ui.place.PlaceScreen
import com.majung.vehicle.ui.proposal.MovePinScreen
import com.majung.vehicle.ui.proposal.ProposalReceivedScreen
import com.majung.vehicle.ui.proposal.ProposalResult
import com.majung.vehicle.ui.proposal.ResultBanner
import com.majung.vehicle.ui.proposal.SAMPLE_FAMILY_PROPOSAL
import com.majung.vehicle.ui.review.ReviewScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Figma 대조용 스냅샷. 화면 추가 시 SCREENS에 한 줄.
private val C12: List<Pair<String, @Composable () -> Unit>> = ProposalResult.entries.map { r ->
    val accepted = r == ProposalResult.ACCEPTED
    val screen: @Composable () -> Unit = {
        MovingScreen(
            "스타필드 하남 · 픽업하기", if (accepted) "북문 로비" else "서문 로비", 12, FamilyStatus("준비됐어요", "14:31"),
            onChangePlace = {}, changed = accepted,
            banner = { ResultBanner(r, "서문 로비", "북문 로비") {} },
        )
    }
    "C12_${r.name.lowercase()}" to screen
}

private val SCREENS: List<Pair<String, @Composable () -> Unit>> = listOf<Pair<String, @Composable () -> Unit>>(
    "C01_home" to { HomeScreen(driving = false) },
    "C02_destination" to { DestinationScreen(initialQuery = "스타필드") },
    "C03_mode" to { ModeScreen("스타필드 하남") },
    "C04_place" to { PlaceScreen("스타필드 하남", VisitMode.PICKUP) },
    "C05_invite" to { InviteScreen("스타필드 하남 · 북문 로비", "http://10.0.2.2:5173/v/ab12cd34") },
    "C06_moving" to {
        MovingScreen("스타필드 하남 · 픽업하기", "북문 로비", 12, FamilyStatus("준비됐어요", "14:31"), onChangePlace = {})
    },
    "C07_park" to { ParkArrivalScreen("스타필드 하남 · 그냥가기", "북측 주차장") },
    "C07_dropoff" to { DropoffArrivalScreen("스타필드 하남 · 내려주기", "북문 승하차 구역") },
    "C07_pickup" to {
        PickupArrivalScreen("스타필드 하남 · 픽업하기", "북문 로비", listOf("엄마", "지민"), "14:48", 2, setOf("엄마"))
    },
    "C08_review" to { ReviewScreen("스타필드 하남 · 픽업하기", "북문 로비", "비 · 14:48 도착 · 가족 2명 탑승") },
    "C09_history" to { HistoryScreen() },
    "C10_move_pin" to { MovePinScreen("서문 로비") },
    "C11_proposal_received" to { ProposalReceivedScreen(SAMPLE_FAMILY_PROPOSAL) },
    "C11_proposal_received_driving" to { ProposalReceivedScreen(SAMPLE_FAMILY_PROPOSAL, driving = true) },
) + C12

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-mdpi")
class ScreenSnapshotTest {
    @Test
    fun snapshots() = SCREENS.forEach { (name, screen) ->
        captureRoboImage("build/outputs/roborazzi/$name.png") { screen() }
    }
}
