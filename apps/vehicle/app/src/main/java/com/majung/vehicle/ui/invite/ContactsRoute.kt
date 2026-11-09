package com.majung.vehicle.ui.invite

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.majung.vehicle.core.contacts.readPhoneContacts
import com.majung.vehicle.ui.VehicleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 연락처 권한을 묻고, 목록을 읽어 ContactsScreen에 넘긴다 */
@Composable
fun ContactsRoute(model: VehicleViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var permitted by remember { mutableStateOf(granted()) }
    var state by remember { mutableStateOf<ContactsState>(if (permitted) ContactsState.Loading else ContactsState.NoPermission) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permitted = ok
        if (!ok) state = ContactsState.NoPermission
    }
    // 처음 열 때 한 번 묻는다. 거절하면 화면의 허용 버튼으로 다시 묻는다
    LaunchedEffect(Unit) { if (!permitted) request.launch(Manifest.permission.READ_CONTACTS) }
    LaunchedEffect(permitted) {
        if (!permitted) return@LaunchedEffect
        state = ContactsState.Loading
        state = ContactsState.Loaded(withContext(Dispatchers.IO) { runCatching { readPhoneContacts(context) }.getOrDefault(emptyList()) })
    }
    ContactsScreen(
        state,
        sending = model.inviteSending,
        sent = model.invitedNumbers,
        notice = model.inviteNotice,
        onSend = model::sendInvite,
        onRequestPermission = { request.launch(Manifest.permission.READ_CONTACTS) },
        onBack = onBack,
    )
}
