import re
with open("app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt", "r") as f:
    text = f.read()

old_clear_btn = """                    OutlinedButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose300),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_limpiar_todo_mercaderias")
                    ) {"""

new_clear_btn = """                    OutlinedButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose300),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp).testTag("btn_limpiar_todo_mercaderias")
                    ) {"""

text = text.replace(old_clear_btn, new_clear_btn)

old_add_btn = """                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_add_mercaderia")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Mercadería", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }"""

new_add_btn = """                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp).testTag("btn_add_mercaderia")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Mercadería", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }"""

text = text.replace(old_add_btn, new_add_btn)

with open("app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt", "w") as f:
    f.write(text)
