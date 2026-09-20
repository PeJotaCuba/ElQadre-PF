import re

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "r") as f:
    text = f.read()

# Fix Tanda creation in AddTandaDialog
text = text.replace(
    'status = "ACTIVA",\n                        jornada = "Jornada Única",',
    'status = "ACTIVA",\n                        jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Única",\n                        jornadaId = uiState.activeJornada?.id ?: 0L,'
)

# Fix Tanda editing (if it has `jornada = "Jornada Única"`)
text = text.replace(
    'jornada = "Jornada Única",\n                        observation = observationText.trim(),',
    'jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Única",\n                        jornadaId = uiState.activeJornada?.id ?: 0L,\n                        observation = observationText.trim(),'
)
# We also need to fix `editarTandaActiva` and `registrarTanda` in MainViewModel.kt

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "w") as f:
    f.write(text)
