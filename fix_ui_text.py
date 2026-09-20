import re

files_to_update = [
    'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminTrialSmsCard.kt',
    'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt',
    'app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt'
]

old_text_1 = 'text = "SOLICITUD PRUEBA ELQADRE (nombre de negocio)\\nDueño: (nombre y apellidos del dueño)\\nMP: (móvil principal)\\nMA: (móvil alternativo)\\nCiudad: (ciudad)",'
new_text_1 = 'text = "SOLICITUD PRUEBA ELQADRE (nombre de negocio)\\nDueño: (nombre y apellidos del dueño)\\nMP: (móvil principal)\\nMA: (móvil alternativo)\\nDVC: (DVC-XXXXXX)\\nCiudad: (ciudad)",'

old_text_2 = 'text = "SOLICITUD DE ACTIVACIÓN (nombre de negocio)\\nDueño: (nombre y apellidos del dueño)\\nMP: (móvil principal)\\nMA: (móvil alternativo)\\nCiudad: (ciudad)",'
new_text_2 = 'text = "SOLICITUD DE ACTIVACIÓN (nombre de negocio)\\nDueño: (nombre y apellidos del dueño)\\nMP: (móvil principal)\\nMA: (móvil alternativo)\\nDVC: (DVC-XXXXXX)\\nCiudad: (ciudad)",'

for file in files_to_update:
    try:
        with open(file, 'r') as f:
            content = f.read()
        
        content = content.replace(old_text_1, new_text_1)
        content = content.replace(old_text_2, new_text_2)
        
        with open(file, 'w') as f:
            f.write(content)
    except:
        pass
        
