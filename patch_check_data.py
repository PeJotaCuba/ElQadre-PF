with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

check_data = """
    fun checkDailyDataUpdates() {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync() ?: return@launch
            
            val cal = java.util.Calendar.getInstance()
            val hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY)
            if (hourOfDay >= 9) {
                // Check Catalog
                if (config.urlCatalogoJson.isNotBlank()) {
                    val lastCatCal = java.util.Calendar.getInstance().apply { timeInMillis = config.lastCatalogoUpdateDate }
                    val isCatSameDay = cal.get(java.util.Calendar.YEAR) == lastCatCal.get(java.util.Calendar.YEAR) &&
                                       cal.get(java.util.Calendar.DAY_OF_YEAR) == lastCatCal.get(java.util.Calendar.DAY_OF_YEAR)
                    
                    if (!isCatSameDay || config.lastCatalogoUpdateStatus != com.example.util.DataUpdateManager.STATUS_SUCCESS) {
                        updateCatalogo(config.urlCatalogoJson)
                    }
                }
                
                // Check Inventory (Barra, Caja)
                if ((user.role == com.example.data.local.model.UserRole.BARRA || user.role == com.example.data.local.model.UserRole.CAJA) && config.urlMercainvJson.isNotBlank()) {
                    val lastInvCal = java.util.Calendar.getInstance().apply { timeInMillis = config.lastMercainvUpdateDate }
                    val isInvSameDay = cal.get(java.util.Calendar.YEAR) == lastInvCal.get(java.util.Calendar.YEAR) &&
                                       cal.get(java.util.Calendar.DAY_OF_YEAR) == lastInvCal.get(java.util.Calendar.DAY_OF_YEAR)
                    
                    if (!isInvSameDay || config.lastMercainvUpdateStatus != com.example.util.DataUpdateManager.STATUS_SUCCESS) {
                        updateMercainv(config.urlMercainvJson)
                    }
                }
            }
        }
    }
"""

if "fun checkDailyDataUpdates" not in text:
    text = text.replace("    fun checkDailyUpdate() {", check_data + "\n    fun checkDailyUpdate() {")
    
    # call checkDailyDataUpdates() after successful login
    text = text.replace(
        "            onSuccess(user)\n        }",
        "            onSuccess(user)\n            checkDailyDataUpdates()\n        }"
    )
    
    # also call checkDailyDataUpdates() if restoring session
    text = text.replace(
        "                        _uiState.update { it.copy(currentUser = user) }\n                    } else {",
        "                        _uiState.update { it.copy(currentUser = user) }\n                        checkDailyDataUpdates()\n                    } else {"
    )

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)
print("Added checkDailyDataUpdates.")
