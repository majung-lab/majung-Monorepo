package com.majung.vehicle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.home.HomeScreen

// 화면 번호는 docs/planning/마중_화면동작정의.md 기준 (C01~C12)
@Composable
fun MajungApp(uxRestriction: UxRestrictionWatcher) {
    val driving by uxRestriction.isRestricted.collectAsState()
    HomeScreen(driving = driving)
}
