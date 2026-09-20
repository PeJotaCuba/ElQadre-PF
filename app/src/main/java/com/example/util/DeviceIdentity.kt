package com.example.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {

    fun getDeterministicDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "UNKNOWN_DEVICE_ID"

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(androidId.toByteArray())

        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ" // Exclude confusing O and I
        val numbers = "23456789" // Exclude 0 and 1
        val allChars = letters + numbers

        val code = StringBuilder()
        for (i in 0 until 6) {
            val byteVal = hashBytes[i].toInt() and 0xFF
            val index = byteVal % allChars.length
            code.append(allChars[index])
        }

        var codeStr = code.toString()
        var hasLetter = codeStr.any { it.isLetter() }
        var hasDigit = codeStr.any { it.isDigit() }

        var offset = 6
        while (!(hasLetter && hasDigit) && offset < hashBytes.size) {
            val byteVal = hashBytes[offset].toInt() and 0xFF
            val index = byteVal % allChars.length
            val newChar = allChars[index]
            
            code.setCharAt(5, newChar)
            codeStr = code.toString()
            hasLetter = codeStr.any { it.isLetter() }
            hasDigit = codeStr.any { it.isDigit() }
            offset++
        }

        if (!hasLetter) code.setCharAt(0, letters[0])
        if (!hasDigit) code.setCharAt(1, numbers[0])

        return code.toString()
    }
}
