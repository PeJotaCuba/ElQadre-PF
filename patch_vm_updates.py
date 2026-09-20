with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

update_methods = """
    fun updateCatalogo(url: String? = null) {
        val finalUrl = url ?: uiState.value.generalConfig?.urlCatalogoJson ?: ""
        if (finalUrl.isBlank()) {
            _uiState.update { it.copy(updateStatusText = "Error: URL de catálogo no configurada") }
            return
        }
        
        _uiState.update { it.copy(updateStatusText = "Comprobando conexión...", isLoading = true) }
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val result = com.example.util.DataUpdateManager.updateCatalogo(db, finalUrl)
            
            val statusText = when (result) {
                com.example.util.DataUpdateManager.UpdateResult.Success -> "Actualización de catálogo completada."
                com.example.util.DataUpdateManager.UpdateResult.NoConnection -> "No hay conexión."
                com.example.util.DataUpdateManager.UpdateResult.DownloadError -> "Error de descarga."
                com.example.util.DataUpdateManager.UpdateResult.InvalidFile -> "Archivo de catálogo inválido."
                com.example.util.DataUpdateManager.UpdateResult.NoNewUpdate -> "No hay actualización nueva de catálogo."
            }
            
            _uiState.update { it.copy(updateStatusText = statusText, isLoading = false) }
        }
    }

    fun updateMercainv(url: String? = null) {
        val finalUrl = url ?: uiState.value.generalConfig?.urlMercainvJson ?: ""
        if (finalUrl.isBlank()) {
            _uiState.update { it.copy(updateStatusText = "Error: URL de inventario no configurada") }
            return
        }
        
        _uiState.update { it.copy(updateStatusText = "Comprobando conexión...", isLoading = true) }
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val result = com.example.util.DataUpdateManager.updateMercainv(db, finalUrl)
            
            val statusText = when (result) {
                com.example.util.DataUpdateManager.UpdateResult.Success -> "Actualización de inventario completada."
                com.example.util.DataUpdateManager.UpdateResult.NoConnection -> "No hay conexión."
                com.example.util.DataUpdateManager.UpdateResult.DownloadError -> "Error de descarga."
                com.example.util.DataUpdateManager.UpdateResult.InvalidFile -> "Archivo de inventario inválido."
                com.example.util.DataUpdateManager.UpdateResult.NoNewUpdate -> "No hay actualización nueva de inventario."
            }
            
            _uiState.update { it.copy(updateStatusText = statusText, isLoading = false) }
        }
    }
"""

if "fun updateCatalogo" not in text:
    text = text.replace("    fun checkDailyUpdate() {", update_methods + "\n    fun checkDailyUpdate() {")

daily_update_logic = """
                if (!isSameDay || config.lastUserUpdateStatus != com.example.util.UserUpdateManager.STATUS_SUCCESS) {
                    updateUsers(config.urlUsuariosJson)
                }
                
                // Also check for catalog and inventory based on logged in user?
                // But checkDailyUpdate is called on start before login. 
                // We'll call checkDailyDataUpdates() separately from inside the view or after login.
"""
with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)

print("MainViewModel updated.")
