with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

import re

# find the nested function and remove it
nested = """    fun updateUrlUsuariosJson(url: String) {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                db.configuracionGeneralDao().updateConfig(config.copy(urlUsuariosJson = url))
                _uiState.update { it.copy(generalConfig = config.copy(urlUsuariosJson = url)) }
            }
        }
    }\n"""

text = text.replace(nested, '')

# add it properly
proper = """
    fun updateUrlUsuariosJson(url: String) {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                db.configuracionGeneralDao().updateConfig(config.copy(urlUsuariosJson = url))
                _uiState.update { it.copy(generalConfig = config.copy(urlUsuariosJson = url)) }
            }
        }
    }
"""

text = text.replace("    fun updateUser(user: User) {", proper + "\n    fun updateUser(user: User) {")

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)

