package com.majung.vehicle.core.uxrestriction

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 제한 켜짐 = 주행 중(또는 공회전), 꺼짐 = 정차 중. 브레이크·기어로 판단하지 않는다. */
class UxRestrictionWatcher(context: Context) {
    private val _isRestricted = MutableStateFlow(false)
    val isRestricted: StateFlow<Boolean> = _isRestricted

    private val car: Car? = runCatching { Car.createCar(context) }.getOrNull()
    private val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager

    init {
        manager?.let { m ->
            _isRestricted.value = m.currentCarUxRestrictions.isRequiresDistractionOptimization
            m.registerListener { r -> _isRestricted.value = r.isRequiresDistractionOptimization }
        }
    }

    fun release() {
        manager?.unregisterListener()
        car?.disconnect()
    }
}
