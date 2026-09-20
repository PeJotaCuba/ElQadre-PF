package com.example.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingComandaSms(
    val senderPhone: String,
    val smsText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val queueId: Long = 0L
)

object SmsComandaBus {
    private val _incomingComandas = MutableSharedFlow<IncomingComandaSms>(extraBufferCapacity = 64)
    val incomingComandas = _incomingComandas.asSharedFlow()

    fun postComandaSms(senderPhone: String, smsText: String, queueId: Long = 0L) {
        _incomingComandas.tryEmit(IncomingComandaSms(senderPhone, smsText, queueId = queueId))
    }
}

