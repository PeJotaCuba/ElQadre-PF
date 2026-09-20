with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

import re

# We will remove both functions from text using regex
text = re.sub(r'    fun updateUsers\(url: String\? = null\) \{.*?(?=data class SalonCartItem|fun checkDailyUpdate)', '', text, flags=re.DOTALL)
text = re.sub(r'    fun checkDailyUpdate\(\) \{.*?        \}\n    \}', '', text, flags=re.DOTALL)

# Ensure no hanging braces right before data class SalonCartItem
text = re.sub(r'\n\}\n\ndata class SalonCartItem', '\n\ndata class SalonCartItem', text)
# Then add one closing brace for MainViewModel, and then the functions
functions_code = """
    fun updateUsers(url: String? = null) {
        val finalUrl = url ?: uiState.value.generalConfig?.urlUsuariosJson ?: ""
        if (finalUrl.isBlank()) {
            _uiState.update { it.copy(updateStatusText = "Error: URL no configurada") }
            return
        }
        
        _uiState.update { it.copy(updateStatusText = "Comprobando conexión...", isLoading = true) }
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val result = com.example.util.UserUpdateManager.updateUsers(db, finalUrl)
            
            val statusText = when (result) {
                is com.example.util.UserUpdateManager.UpdateResult.Success -> "Actualización completada."
                is com.example.util.UserUpdateManager.UpdateResult.NoConnection -> "No hay conexión."
                is com.example.util.UserUpdateManager.UpdateResult.DownloadError -> "Error de descarga."
                is com.example.util.UserUpdateManager.UpdateResult.InvalidFile -> "Archivo inválido."
                is com.example.util.UserUpdateManager.UpdateResult.NoNewUpdate -> "No hay actualización nueva."
            }
            
            _uiState.update { it.copy(updateStatusText = statusText, isLoading = false) }
        }
    }

    fun checkDailyUpdate() {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync() ?: return@launch
            
            if (config.urlUsuariosJson.isBlank()) return@launch
            
            val cal = java.util.Calendar.getInstance()
            val hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY)
            
            if (hourOfDay >= 9) {
                val lastUpdate = config.lastUserUpdateDate
                val lastUpdateCal = java.util.Calendar.getInstance()
                lastUpdateCal.timeInMillis = lastUpdate
                
                val isSameDay = cal.get(java.util.Calendar.YEAR) == lastUpdateCal.get(java.util.Calendar.YEAR) &&
                                cal.get(java.util.Calendar.DAY_OF_YEAR) == lastUpdateCal.get(java.util.Calendar.DAY_OF_YEAR)
                
                if (!isSameDay || config.lastUserUpdateStatus != com.example.util.UserUpdateManager.STATUS_SUCCESS) {
                    updateUsers(config.urlUsuariosJson)
                }
            }
        }
    }
}

data class SalonCartItem"""

text = text.replace('data class SalonCartItem', functions_code)

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)
print("Cleaned up and inserted.")
