with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'r') as f:
    text = f.read()

text = text.replace(".pointerInput(Unit) {}", ".androidx.compose.foundation.clickable(enabled = false) {}")

with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'w') as f:
    f.write(text)
