package com.example.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsTransferBus {
    private val _incomingTransfers = MutableSharedFlow<ParsedTransferSms>(extraBufferCapacity = 64)
    val incomingTransfers = _incomingTransfers.asSharedFlow()

    fun postTransfer(parsed: ParsedTransferSms) {
        _incomingTransfers.tryEmit(parsed)
    }
}
