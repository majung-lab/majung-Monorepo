package com.majung.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.MajungApp

class MainActivity : ComponentActivity() {
    private lateinit var uxRestriction: UxRestrictionWatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uxRestriction = UxRestrictionWatcher(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MajungApp(uxRestriction)
            }
        }
    }

    override fun onDestroy() {
        uxRestriction.release()
        super.onDestroy()
    }
}
