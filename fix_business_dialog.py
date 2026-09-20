with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminNegociosSection.kt', 'r') as f:
    content = f.read()

import_text = "import com.example.util.ContactHelper\n"
if "import com.example.util.ContactHelper" not in content:
    content = content.replace("package com.example.ui.screens.superadmin\n", "package com.example.ui.screens.superadmin\n" + import_text)

old_movil = """                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Principal:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.phone.ifBlank { "—" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }"""

new_movil = """                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Principal:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = ContactHelper.formatPhoneNumberWithContact(context, currentBiz.phone.ifBlank { "—" }),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }"""
content = content.replace(old_movil, new_movil)

old_movil_alt = """                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Alternativo:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.phoneAlt.ifBlank { "—" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }"""
new_movil_alt = """                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Alternativo:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = ContactHelper.formatPhoneNumberWithContact(context, currentBiz.phoneAlt.ifBlank { "—" }),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }"""
content = content.replace(old_movil_alt, new_movil_alt)

# Add Dueno info and "Agregar a contactos" action below the row
dueno_block = """
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            if (currentBiz.dueno.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dueño / Propietario:", fontSize = 11.sp, color = Slate500)
                                        Text(
                                            text = currentBiz.dueno,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    }
                                    
                                    if (currentBiz.phone.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                ContactHelper.addContactIntent(context, currentBiz.dueno, currentBiz.phone)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Agregar a Contactos", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
"""

old_spacer_after_row = """                            Spacer(modifier = Modifier.height(8.dp))

                            Row("""

content = content.replace(old_spacer_after_row, dueno_block + "\n" + old_spacer_after_row)

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminNegociosSection.kt', 'w') as f:
    f.write(content)
