import re

files = ['app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt',
         'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt']

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    # Remove the old SUCCESS & SHARE OVERLAY completely
    # We will replace it with a simple FileGeneratedSuccessDialog from Dashboard or create a local one.
    
    success_block = re.search(r"// SUCCESS & SHARE OVERLAY.*?(?=\n})", content, re.DOTALL)
    if success_block:
        content = content.replace(success_block.group(0), "")
        
    # Now append our simple success block
    simple_success = """
    // SUCCESS & SHARE OVERLAY
    generatedFileToShare?.let { file ->
        FileGeneratedSuccessDialogLocal(
            file = file,
            onShare = {
                SuperAdminSmsHelper.shareJsonFile(context, file, "Compartir JSON")
            },
            onDismiss = {
                generatedFileToShare = null
            }
        )
    }
    """
    
    # Also add the Composable definition at the end of the file
    composable_def = """
@Composable
private fun FileGeneratedSuccessDialogLocal(
    file: File,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archivo Generado Exitosamente") },
        text = { Text("Se ha guardado ${file.name}. ¿Desea compartirlo ahora?") },
        confirmButton = {
            Button(onClick = { onShare(); onDismiss() }) { Text("Compartir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
"""
    
    # insert simple success before the closing brace of the main composable
    # The main composable ends where? The last `}` before the first `@Composable private fun`
    idx = content.find("private fun ActivationProcessDialog")
    if idx == -1:
        idx = content.find("private fun UsuariosProcessDialog")
        
    if idx != -1:
        # find the `}` right before idx
        brace_idx = content.rfind("}", 0, idx)
        if brace_idx != -1:
            content = content[:brace_idx] + simple_success + "\n}\n" + content[idx:] + composable_def
            
    with open(filepath, 'w') as f:
        f.write(content)
