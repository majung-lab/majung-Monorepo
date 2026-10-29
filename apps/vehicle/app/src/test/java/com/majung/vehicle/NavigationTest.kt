package com.majung.vehicle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.MajungApp
import com.majung.vehicle.ui.theme.LocalStaticMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 픽업하기 흐름 + 시뮬레이션 (가족 상태 변화, 가족 제안)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-mdpi")
class NavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pickupFlow() {
        start()
        toMoving()
        rule.onNodeWithText("도착했어요").performClick()
        rule.onNodeWithText("엄마").performClick()
        rule.onNodeWithText("지민").performClick()
        rule.onNodeWithText("탑승 완료").performClick()
        rule.onNodeWithText("건너뛰기").performClick()
        rule.onNodeWithText("어디로 갈까요?").assertExists()
    }

    // C06에서 25초 지나면 가족 제안(C11) → 수락 → 안내 계속 → 바뀐 장소
    @Test
    fun familyProposalArrives() {
        start()
        toMoving()
        rule.onNodeWithText("북문 로비").assertExists()
        rule.onNodeWithText("아직 링크를 안 열었어요").assertExists()
        rule.mainClock.advanceTimeBy(15_000)
        rule.onNodeWithText("준비됐어요").assertExists()
        rule.mainClock.advanceTimeBy(11_000)
        rule.onNodeWithText("가족이 만날 곳을 바꾸자고 해요").assertExists()
        rule.onNodeWithText("서문으로 바꾸기").performClick()
        rule.onNodeWithText("안내 계속").performClick()
        rule.onNodeWithText("서문 로비").assertExists()
        rule.onNodeWithText("바뀐 장소").assertExists()
    }

    // C10 대기 없이 뒤로 → C06, 다시 들어가 핀을 안 옮기면 보낼 수 없음
    @Test
    fun movePinNeedsMove() {
        start()
        toMoving()
        rule.onNodeWithText("위치 바꾸기").performClick()
        rule.onNodeWithText("핀을 옮겨 주세요").assertExists()
        rule.onNodeWithText("여기로 조정하기").assertDoesNotExist()
        rule.onNodeWithContentDescription("뒤로").performClick()
        rule.onNodeWithText("도착했어요").assertExists()
    }

    private fun start() = rule.setContent {
        // Robolectric엔 지도 타일이 없어 목업 지도로
        CompositionLocalProvider(LocalStaticMap provides true) {
            MajungApp(UxRestrictionWatcher(ApplicationProvider.getApplicationContext()))
        }
    }

    private fun toMoving() {
        rule.onNodeWithText("목적지 입력").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("스타필드")
        rule.onNodeWithText("스타필드 하남").performClick()
        rule.onNodeWithText("픽업하기").performClick()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("픽업 장소로 출발").performClick()
    }
}
