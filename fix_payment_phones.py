with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminPaymentSmsCard.kt', 'r') as f:
    content = f.read()

import_text = "import com.example.util.ContactHelper\n"
content = content.replace("package com.example.ui.screens.superadmin\n", "package com.example.ui.screens.superadmin\n" + import_text)

content = content.replace('text = "Teléfono: ${payment.phoneNumber}', 'text = "Teléfono: ${ContactHelper.formatPhoneNumberWithContact(context, payment.phoneNumber)}')
content = content.replace('text = "${payment.phoneNumber} (${if', 'text = "${ContactHelper.formatPhoneNumberWithContact(context, payment.phoneNumber)} (${if')

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminPaymentSmsCard.kt', 'w') as f:
    f.write(content)
