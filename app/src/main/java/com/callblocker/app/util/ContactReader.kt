package com.callblocker.app.util

import android.content.Context
import android.provider.ContactsContract

data class ContactInfo(
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String
)

object ContactReader {
    fun readAllContacts(context: Context): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return contacts

        cursor.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                val rawNumber = if (numberIndex >= 0) it.getString(numberIndex)?.trim() ?: "" else ""
                if (rawNumber.isNotBlank()) {
                    contacts.add(ContactInfo(
                        name = name,
                        phoneNumber = rawNumber,
                        normalizedNumber = normalizePhoneNumber(rawNumber)
                    ))
                }
            }
        }
        return contacts
    }

    fun normalizePhoneNumber(number: String): String {
        return number.replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "").replace("+", "")
    }
}
