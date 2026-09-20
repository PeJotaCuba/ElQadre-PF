with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace("    fun checkDailyUpdate() {\n    fun clearUpdateStatus() {\n        _uiState.update { it.copy(updateStatusText = null) }\n    }", 
"""    fun clearUpdateStatus() {
        _uiState.update { it.copy(updateStatusText = null) }
    }

    fun checkDailyUpdate() {""")

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(text)
