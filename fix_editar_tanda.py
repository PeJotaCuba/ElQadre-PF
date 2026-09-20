import re

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

old_editar = """    fun editarTandaActiva(
        newTanda: Tanda,
        oldConsumos: List<Triple<Long, Double, String>>,
        newConsumos: List<Triple<Long, Double, String>>
    ) {
        viewModelScope.launch {
            try {
                // 1. Revertir consumos anteriores
                oldConsumos.forEach { (mpId, oldQty, _) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val restoredStock = raw.stock + oldQty
                        val updatedMp = raw.copy(stock = restoredStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "AJUSTE_TANDA_REVERTIR",
                            quantity = oldQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Reversión por edición de tanda ${newTanda.uuid}",
                            resultingStock = restoredStock
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }

                // 2. Aplicar nuevos consumos
                newConsumos.forEach { (mpId, newQty, originalText) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val nextStock = (raw.stock - newQty).coerceAtLeast(0.0)
                        val updatedMp = raw.copy(stock = nextStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "TANDA_CONSUMO",
                            quantity = newQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Consumo actualizado tanda ${newTanda.uuid} (${originalText})",
                            resultingStock = nextStock
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }"""

new_editar = """    fun editarTandaActiva(
        newTanda: Tanda,
        oldConsumos: List<Triple<Long, Double, String>>,
        newConsumos: List<Triple<Long, Double, String>>
    ) {
        viewModelScope.launch {
            try {
                // 0. Validar stock reconciliado (stock actual + consumos viejos - nuevos consumos)
                val insufficientStock = newConsumos.find { (mpId, newQty, _) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    val oldQty = oldConsumos.find { it.first == mpId }?.second ?: 0.0
                    raw == null || (raw.stock + oldQty - newQty) < 0.0
                }
                if (insufficientStock != null) {
                    val raw = _uiState.value.materiasPrimas.find { it.id == insufficientStock.first }
                    val rawName = raw?.name ?: "Materia prima desconocida"
                    _uiState.update { it.copy(errorMessage = "Stock insuficiente para $rawName en la edición. Tanda no modificada.") }
                    return@launch
                }

                // 1. Revertir consumos anteriores
                oldConsumos.forEach { (mpId, oldQty, _) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val restoredStock = raw.stock + oldQty
                        val updatedMp = raw.copy(stock = restoredStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "AJUSTE_TANDA_REVERTIR",
                            quantity = oldQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Reversión por edición de tanda ${newTanda.uuid}",
                            resultingStock = restoredStock,
                            jornadaId = newTanda.jornadaId
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }

                // 2. Aplicar nuevos consumos
                newConsumos.forEach { (mpId, newQty, originalText) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val nextStock = raw.stock - newQty
                        val updatedMp = raw.copy(stock = nextStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "TANDA_CONSUMO",
                            quantity = newQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Consumo actualizado tanda ${newTanda.uuid} (${originalText})",
                            resultingStock = nextStock,
                            jornadaId = newTanda.jornadaId
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }"""

text = text.replace(old_editar, new_editar)

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)
