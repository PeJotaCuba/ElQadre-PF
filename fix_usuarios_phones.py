with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'r') as f:
    content = f.read()

import_text = "import com.example.util.ContactHelper\n"
content = content.replace("package com.example.ui.screens.superadmin\n", "package com.example.ui.screens.superadmin\n" + import_text)

# Fix sender address rendering
content = content.replace('Text(request.senderAddress,', 'Text(ContactHelper.formatPhoneNumberWithContact(context, request.senderAddress),')

content = content.replace('Text(request.numeroMovil.ifBlank { request.senderAddress }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)',
                          'Text(ContactHelper.formatPhoneNumberWithContact(context, request.numeroMovil.ifBlank { request.senderAddress }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)')

content = content.replace('Text(request.numeroMovilAlt, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)',
                          'Text(ContactHelper.formatPhoneNumberWithContact(context, request.numeroMovilAlt), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)')

content = content.replace('Text(business?.phone ?: request.numeroMovil, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)',
                          'Text(ContactHelper.formatPhoneNumberWithContact(context, business?.phone ?: request.numeroMovil), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)')

content = content.replace('Text(altText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)',
                          'Text(ContactHelper.formatPhoneNumberWithContact(context, altText), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)')

content = content.replace('Text(text = "Solicitante: ${request.solicitante} (${request.senderAddress})"', 
                          'Text(text = "Solicitante: ${request.solicitante} (${ContactHelper.formatPhoneNumberWithContact(context, request.senderAddress)})"')


with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'w') as f:
    f.write(content)
