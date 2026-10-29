package com.majung.vehicle

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.MajungApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 픽업하기 한 바퀴: C01 → C02 → C03 → C04 → C05 → C06 → C07 → C08 → C01
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-mdpi")
class NavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pickupFlow() {
        rule.setContent { MajungApp(UxRestrictionWatcher(ApplicationProvider.getApplicationContext())) }
        rule.onNodeWithText("목적지 입력").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("스타필드")
        rule.onNodeWithText("스타필드 하남").performClick()
        rule.onNodeWithText("픽업하기").performClick()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("픽업 장소로 출발").performClick()
        rule.onNodeWithText("도착했어요").performClick()
        rule.onNodeWithText("엄마").performClick()
        rule.onNodeWithText("지민").performClick()
        rule.onNodeWithText("탑승 완료").performClick()
        rule.onNodeWithText("건너뛰기").performClick()
        rule.onNodeWithText("어디로 갈까요?").assertExists()
    }
}
