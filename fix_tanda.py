with open("app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt", "r") as f:
    text = f.read()

# We need to find the registrarTanda function
import re

print("Found registrarTanda:", "registrarTanda(" in text)
