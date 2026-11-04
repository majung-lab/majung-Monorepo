package com.majung.vehicle

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.majung.vehicle.ui.VehicleViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.majung.vehicle.core.notice.OverlayBanner
import com.majung.vehicle.core.notice.VisitNoticeService
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.MajungApp
import com.majung.vehicle.ui.theme.ScaleToPanel

class MainActivity : ComponentActivity() {
    private lateinit var uxRestriction: UxRestrictionWatcher
    private lateinit var model: VehicleViewModel
    private var locations: LocationManager? = null
    private val locationListener = LocationListener { model.publishLocation(it) }
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uxRestriction = UxRestrictionWatcher(this)
        model = ViewModelProvider(this)[VehicleViewModel::class.java]
        requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ScaleToPanel { MajungApp(uxRestriction, model) }
            }
        }
    }

    private fun startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locations = getSystemService(LocationManager::class.java)
        try {
            locations?.getProviders(true)?.filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }?.forEach { provider ->
                locations?.getLastKnownLocation(provider)?.let(model::publishLocation)
                // 정차 중에도 갱신되도록 이동 거리 조건을 두지 않는다
                locations?.requestLocationUpdates(provider, 15_000L, 0f, locationListener)
            }
        } catch (_: SecurityException) { }
    }

    override fun onStart() {
        super.onStart()
        VisitNoticeService.onAppShown(this)
        OverlayBanner.setAppVisible(this, true)
    }

    override fun onStop() {
        VisitNoticeService.appVisible = false
        OverlayBanner.setAppVisible(this, false)
        super.onStop()
    }

    override fun onDestroy() {
        locations?.removeUpdates(locationListener)
        uxRestriction.release()
        super.onDestroy()
    }
}
