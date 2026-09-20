import re

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "r") as f:
    text = f.read()

text = text.replace(
    'status = if (item.actualYield > 0.0 && item.actualYield != item.expectedYield) "CERRADA" else "ACTIVA",\n                            jornada = "Jornada Única",\n                            observation = "Importada desde texto de Cocina",',
    'status = if (item.actualYield > 0.0 && item.actualYield != item.expectedYield) "CERRADA" else "ACTIVA",\n                            jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Única",\n                            jornadaId = uiState.activeJornada?.id ?: 0L,\n                            observation = "Importada desde texto de Cocina",'
)

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "w") as f:
    f.write(text)
