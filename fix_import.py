with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.licensing.SuperAdminPaymentHelperpackage com.example.ui.screens.superadmin", "package com.example.ui.screens.superadmin\nimport com.example.licensing.SuperAdminPaymentHelper\n")

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'w') as f:
    f.write(content)
