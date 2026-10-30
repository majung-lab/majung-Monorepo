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
import com.majung.vehicle.core.uxrestriction.UxRestrictionWatcher
import com.majung.vehicle.ui.MajungApp

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
        requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MajungApp(uxRestriction, model)
            }
        }
    }

    private fun startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locations = getSystemService(LocationManager::class.java)
        try {
            locations?.getProviders(true)?.filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }?.forEach { provider ->
                locations?.requestLocationUpdates(provider, 15_000L, 10f, locationListener)
            }
        } catch (_: SecurityException) { }
    }

    override fun onDestroy() {
        locations?.removeUpdates(locationListener)
        uxRestriction.release()
        super.onDestroy()
    }
}
