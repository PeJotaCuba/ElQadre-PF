package com.example.util

import java.security.SecureRandom

object DeviceUtils {
    private const val PREFIX = "DVC-"
    private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excluded ambiguous chars like O,0,1,I

    fun generateDeviceId(): String {
        val random = SecureRandom()
        val builder = StringBuilder(PREFIX)
        repeat(6) {
            val index = random.nextInt(CHARACTERS.length)
            builder.append(CHARACTERS[index])
        }
        return builder.toString()
    }
}
