package com.majung.vehicle.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// C01 홈 — 주행 중엔 가림
@Composable
fun HomeScreen(driving: Boolean) {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (driving) "정차 후 이용해 주세요" else "어디로 갈까요?")
        }
    }
}
