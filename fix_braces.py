def fix_braces(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    open_braces = content.count('{')
    close_braces = content.count('}')
    
    if open_braces > close_braces:
        content += '\n}' * (open_braces - close_braces)
    elif close_braces > open_braces:
        # this is harder, just strip from end if possible
        pass

    with open(filepath, 'w') as f:
        f.write(content)

fix_braces('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt')
fix_braces('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt')
