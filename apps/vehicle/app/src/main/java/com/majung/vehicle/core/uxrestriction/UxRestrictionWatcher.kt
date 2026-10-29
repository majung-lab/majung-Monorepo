package com.majung.vehicle.core.uxrestriction

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 제한 켜짐 = 주행 중(또는 공회전), 꺼짐 = 정차 중. 브레이크·기어로 판단하지 않는다. */
class UxRestrictionWatcher(context: Context) {
    private val _isRestricted = MutableStateFlow(false)
    val isRestricted: StateFlow<Boolean> = _isRestricted

    // 자동차가 아닌 기기(휴대폰 에뮬레이터·테스트)엔 android.car 클래스가 없다 → 항상 정차 중
    private val isCar = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    // android.car 타입은 이 분기 안에서만 건드린다 (형 검사만으로도 클래스를 불러온다)
    private var release: () -> Unit = {}

    init {
        if (isCar) runCatching {
            val car = Car.createCar(context)
            val m = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _isRestricted.value = m.currentCarUxRestrictions.isRequiresDistractionOptimization
            m.registerListener { r -> _isRestricted.value = r.isRequiresDistractionOptimization }
            release = { m.unregisterListener(); car.disconnect() }
        }
    }

    fun release() = release.invoke()
}
