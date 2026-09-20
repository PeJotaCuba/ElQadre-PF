import re

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

old_registrar = """    fun registrarTanda(
        tanda: Tanda,
        consumos: List<Triple<Long, Double, String>> // Triple of (materiaPrimaId, quantityToDeductInInventoryUnit, originalQuantityWithUnitText)
    ) {
        viewModelScope.launch {
            try {
                // 1. Guardar la tanda
                repository.insertTanda(tanda)

                // 2. Descontar stock de materias primas y registrar movimientos
                consumos.forEach { (mpId, qtyToDeduct, originalText) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val nextStock = (raw.stock - qtyToDeduct).coerceAtLeast(0.0)
                        val updatedMp = raw.copy(stock = nextStock)
                        repository.updateMateriaPrima(updatedMp)

                        // Registrar movimiento
                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "TANDA_CONSUMO",
                            quantity = qtyToDeduct,
                            unit = raw.unit,
                            responsibleUser = tanda.responsibleUser,
                            notes = "Consumo para tanda ${tanda.uuid} (${originalText})",
                            resultingStock = nextStock
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }"""

new_registrar = """    fun registrarTanda(
        tanda: Tanda,
        consumos: List<Triple<Long, Double, String>> // Triple of (materiaPrimaId, quantityToDeductInInventoryUnit, originalQuantityWithUnitText)
    ) {
        viewModelScope.launch {
            try {
                // 0. Validar stock
                val insufficientStock = consumos.find { (mpId, qtyToDeduct, _) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    raw == null || raw.stock < qtyToDeduct
                }
                if (insufficientStock != null) {
                    val raw = _uiState.value.materiasPrimas.find { it.id == insufficientStock.first }
                    val rawName = raw?.name ?: "Materia prima desconocida"
                    _uiState.update { it.copy(errorMessage = "Stock insuficiente para $rawName (requiere ${"%.2f".format(insufficientStock.second)}). Tanda no activada.") }
                    return@launch
                }

                // 1. Guardar la tanda
                val activeJornada = _uiState.value.activeJornada
                val tandaToInsert = if (tanda.jornadaId == 0L && activeJornada != null) {
                    tanda.copy(jornada = "Jornada #${activeJornada.id}", jornadaId = activeJornada.id)
                } else tanda
                repository.insertTanda(tandaToInsert)

                // 2. Descontar stock de materias primas y registrar movimientos
                consumos.forEach { (mpId, qtyToDeduct, originalText) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val nextStock = raw.stock - qtyToDeduct
                        val updatedMp = raw.copy(stock = nextStock)
                        repository.updateMateriaPrima(updatedMp)

                        // Registrar movimiento
                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "TANDA_CONSUMO",
                            quantity = qtyToDeduct,
                            unit = raw.unit,
                            responsibleUser = tandaToInsert.responsibleUser,
                            notes = "Consumo para tanda ${tandaToInsert.uuid} (${originalText})",
                            resultingStock = nextStock,
                            jornadaId = tandaToInsert.jornadaId
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }"""

text = text.replace(old_registrar, new_registrar)

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)
