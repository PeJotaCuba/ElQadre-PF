package com.example.util

import com.example.licensing.TrialSmsRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SuperAdminSmsBus {
    private val _incomingTrialRequests = MutableSharedFlow<TrialSmsRequest>(extraBufferCapacity = 64)
    val incomingTrialRequests = _incomingTrialRequests.asSharedFlow()

    fun postTrialRequest(request: TrialSmsRequest) {
        _incomingTrialRequests.tryEmit(request)
    }
}
