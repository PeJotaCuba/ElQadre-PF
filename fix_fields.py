import re

files = ['app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt',
         'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt']

for filepath in files:
    with open(filepath, 'r') as f:
        content = f.read()

    # Fix property names
    content = content.replace("movilPrincipal", "numeroMovil")
    content = content.replace("movilAlternativo", "numeroMovilAlt")
    
    # Remove TrialSmsSuccessOverlayDialog since we use FileGeneratedSuccessDialog or nothing?
    # Wait, in ActivationProcessDialog, I use `onApproved = { file -> generatedFileToShare = file }`.
    # And then in TrialSmsCard it had `TrialSmsSuccessOverlayDialog`. We can just rename `TrialSmsSuccessOverlayDialog` to `ActivationSuccessOverlayDialog` and fix the arguments.
    # Actually, the success overlay wants a `BusinessRecord`, but we might just want a generic success.
    # It's better to just drop TrialSmsSuccessOverlayDialog and use a simple success dialog or the existing one.
    content = content.replace("TrialSmsSuccessOverlayDialog", "FileGeneratedSuccessDialogFallback")
    
    # We will redefine FileGeneratedSuccessDialogFallback at the end of the file.
    
    with open(filepath, 'w') as f:
        f.write(content)
