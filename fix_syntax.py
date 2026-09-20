import re

files = ['app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt',
         'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt']

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    # Find the malformed block inside ActivationProcessDialog call
    malformed_block = re.search(r"// SUCCESS & SHARE OVERLAY\s+generatedFileToShare\?\.let \{ file ->.*?\}\s+\}", content, re.DOTALL)
    if malformed_block:
        block_text = malformed_block.group(0)
        content = content.replace(block_text, "")
        
        # Now find the true end of the main Composable
        # The main Composable ends just before `/**\n * Cuadro superpuesto amplio y legible para ESCANEAR`
        end_idx = content.find("/**\n * Cuadro superpuesto amplio y legible para ESCANEAR")
        if end_idx != -1:
            # We want to insert it right before this block
            # Actually, there's a `}` right before this comment block that closes the main Composable.
            brace_idx = content.rfind("}", 0, end_idx)
            if brace_idx != -1:
                content = content[:brace_idx] + "\n" + block_text + "\n" + content[brace_idx:]
                
    with open(filepath, 'w') as f:
        f.write(content)

