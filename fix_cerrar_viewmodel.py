import re

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

text = text.replace(
    'Producción Esperada: ${tanda.estimatedYield.toInt()}',
    'Producción Esperada: ${tanda.expectedYield.toInt()}'
)

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)
