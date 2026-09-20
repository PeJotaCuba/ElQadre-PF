with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'r') as f:
    content = f.read()

# Fix buildPagoRecibidoSms
content = content.replace('        if (dvc.isNotBlank()) builder.append("DVC: ${if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"}\\n")\n        builder.append("Ciudad: ${ciudad.trim()}")\n        return builder.toString().trim()\n    }', '        builder.append("Ciudad: ${ciudad.trim()}")\n        return builder.toString().trim()\n    }')

# Fix parsePagoRecibidoSms by adding var dvc = ""
content = content.replace('        var ciudad = ""\n\n        // Extract business name', '        var ciudad = ""\n        var dvc = ""\n\n        // Extract business name')

# Fix UI components unresolved 'context' error
# SuperAdminActivationSmsCard.kt
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'r') as f:
    act_content = f.read()
act_content = act_content.replace('Text(ContactHelper.formatPhoneNumberWithContact(context, request.senderAddress),', 'Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.senderAddress),')
act_content = act_content.replace('ContactHelper.formatPhoneNumberWithContact(context, ', 'ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, ')
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'w') as f:
    f.write(act_content)

# SuperAdminTrialSmsCard.kt
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminTrialSmsCard.kt', 'r') as f:
    trial_content = f.read()
trial_content = trial_content.replace('Text(ContactHelper.formatPhoneNumberWithContact(context, request.senderAddress),', 'Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.senderAddress),')
trial_content = trial_content.replace('ContactHelper.formatPhoneNumberWithContact(context, ', 'ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, ')
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminTrialSmsCard.kt', 'w') as f:
    f.write(trial_content)

# SuperAdminUsuariosSmsCard.kt
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'r') as f:
    user_content = f.read()
user_content = user_content.replace('Text(ContactHelper.formatPhoneNumberWithContact(context, request.senderAddress),', 'Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.senderAddress),')
user_content = user_content.replace('ContactHelper.formatPhoneNumberWithContact(context, ', 'ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, ')
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'w') as f:
    f.write(user_content)

# SuperAdminPaymentSmsCard.kt
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminPaymentSmsCard.kt', 'r') as f:
    pay_content = f.read()
pay_content = pay_content.replace('ContactHelper.formatPhoneNumberWithContact(context, ', 'ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, ')
with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminPaymentSmsCard.kt', 'w') as f:
    f.write(pay_content)

with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'w') as f:
    f.write(content)

