with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'r') as f:
    content = f.read()

idx = content.find('package')
if idx != -1:
    content = content[idx:]

content = content.replace("package com.example.ui.screens.superadmin", "package com.example.ui.screens.superadmin\nimport com.example.licensing.SuperAdminPaymentHelper")

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'w') as f:
    f.write(content)
