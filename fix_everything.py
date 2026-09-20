import re

files = ['app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt',
         'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt']

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    # Move the // SUCCESS & SHARE OVERLAY back up
    success_block_match = re.search(r"// SUCCESS & SHARE OVERLAY\s+generatedFileToShare\?\.let \{ file ->.*?\}\s+\}", content, re.DOTALL)
    if success_block_match:
        success_block = success_block_match.group(0)
        content = content.replace(success_block, "")
        
        # find the end of SuperAdminActivationSmsCard
        idx = content.find("// REVIEW & PROCESS CONFIRMATION DIALOG")
        if idx != -1:
            end_idx = content.find("}", idx)
            # wait, it's `requestToReview?.let { ... }` then `}`.
            # let's just insert it right after `requestToReview?.let { ... }` closing brace.
            let_end = content.find("    }", idx)
            if let_end != -1:
                content = content[:let_end + 5] + "\n" + success_block + "\n" + content[let_end + 5:]

    # Remove the extra `}` and `@Composable private fun FileGeneratedSuccessDialogLocal`
    dialog_idx = content.find("@Composable\nprivate fun FileGeneratedSuccessDialogLocal")
    if dialog_idx != -1:
        # the extra `}` is before this. Let's find it.
        extra_brace_idx = content.rfind("}", 0, dialog_idx)
        if extra_brace_idx != -1:
            content = content[:extra_brace_idx] + content[extra_brace_idx+1:]
            
    # Fix unresolved references in the rest of the file
    content = content.replace("movilPrincipal", "numeroMovil")
    content = content.replace("movilAlternativo", "numeroMovilAlt")
    content = content.replace("TrialSmsSuccessOverlayDialog", "FileGeneratedSuccessDialogLocal")
    
    with open(filepath, 'w') as f:
        f.write(content)

