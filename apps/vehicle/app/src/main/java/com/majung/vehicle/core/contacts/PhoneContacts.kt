package com.majung.vehicle.core.contacts

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

/** 차량에서는 블루투스로 연결된 휴대폰의 연락처가 동기화돼 들어온다 */
data class PhoneContact(val name: String, val number: String)

/** 문자를 받을 수 있는 휴대폰 번호(010 등)만, 이름순으로, 같은 번호는 한 번만 */
fun readPhoneContacts(context: Context): List<PhoneContact> {
    val projection = arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER)
    val seen = HashSet<String>()
    val result = ArrayList<PhoneContact>()
    context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
        while (c.moveToNext()) {
            val number = c.getString(1) ?: continue
            val digits = mobileDigits(number) ?: continue
            if (seen.add(digits)) result += PhoneContact(c.getString(0)?.takeIf { it.isNotBlank() } ?: formatPhone(digits), formatPhone(digits))
        }
    }
    return result
}

/** 서버와 같은 기준: +82·하이픈·공백을 정리하고 01X로 시작하는 10~11자리만 */
fun mobileDigits(input: String): String? {
    var digits = input.filter { it.isDigit() || it == '+' }
    if (digits.startsWith("+82")) digits = "0" + digits.drop(3)
    return digits.takeIf { Regex("^01[016789][0-9]{7,8}$").matches(it) }
}

fun formatPhone(digits: String): String =
    if (digits.length == 11) "${digits.take(3)}-${digits.substring(3, 7)}-${digits.takeLast(4)}"
    else "${digits.take(3)}-${digits.substring(3, 6)}-${digits.takeLast(4)}"
