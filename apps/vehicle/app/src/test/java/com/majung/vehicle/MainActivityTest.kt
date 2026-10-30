package com.majung.vehicle

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.majung.vehicle.ui.VehicleViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test fun defaultActivityFactoryCanCreateVehicleViewModel() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        try {
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[VehicleViewModel::class.java]
        } finally { store.clear() }
    }
}
