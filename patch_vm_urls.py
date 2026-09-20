with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
"""    fun updateUrlUsuariosJson(url: String) {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                db.configuracionGeneralDao().updateConfig(config.copy(urlUsuariosJson = url))
                _uiState.update { it.copy(generalConfig = config.copy(urlUsuariosJson = url)) }
            }
        }
    }""",
"""    fun updateUrls(urlUsuarios: String, urlCatalogo: String, urlMercainv: String) {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                val newConfig = config.copy(
                    urlUsuariosJson = urlUsuarios,
                    urlCatalogoJson = urlCatalogo,
                    urlMercainvJson = urlMercainv
                )
                db.configuracionGeneralDao().updateConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig) }
            }
        }
    }"""
)

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)
print("MainViewModel patched.")
