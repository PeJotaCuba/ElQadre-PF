with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.TextButton")
text = text.replace("androidx.compose.material3.AlertDialog", "AlertDialog")

with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'w') as f:
    f.write(text)
