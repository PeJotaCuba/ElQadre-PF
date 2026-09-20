    fun registrarTransferenciaManual(parsed: com.example.util.ParsedTransferSms, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getTransferenciaByTransactionNumber(parsed.transactionNumber)
            if (existing != null) {
                onError("La transferencia ${parsed.transactionNumber} ya existe.")
                return@launch
            }
            val username = _uiState.value.currentUser?.username ?: "cajero"
            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L
            val transferencia = com.example.data.local.model.Transferencia(
                transactionNumber = parsed.transactionNumber,
                jornadaId = activeJornadaId,
                amount = parsed.amount,
                currency = parsed.currency,
                phoneNumber = parsed.phoneNumber,
                recipientAccount = parsed.recipientAccount,
                smsDate = parsed.dateStr,
                receivedAt = System.currentTimeMillis(),
                cajeroUsername = username,
                status = "NO ASOCIADA",
                rawSmsBody = parsed.rawText,
                isManual = true,
                source = "MANUAL_EXTERNA"
            )
            repository.insertTransferencia(transferencia)
            onSuccess()
        }
    }
