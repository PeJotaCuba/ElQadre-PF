import re

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

text = text.replace(
    'resultingStock = nextStock,\n                            jornadaId = tandaToInsert.jornadaId',
    'resultingStock = nextStock'
)

text = text.replace(
    'resultingStock = restoredStock,\n                            jornadaId = newTanda.jornadaId',
    'resultingStock = restoredStock'
)

text = text.replace(
    'resultingStock = nextStock,\n                            jornadaId = newTanda.jornadaId',
    'resultingStock = nextStock'
)

with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "w") as f:
    f.write(text)
