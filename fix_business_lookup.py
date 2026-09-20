import re

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "SuperAdminBusinessManager.getBusinessByDvcOrName(context, request.dvc, request.nombreNegocio)",
    "SuperAdminBusinessManager.getBusinessByDvc(context, request.dvc) ?: SuperAdminBusinessManager.getBusinesses(context).firstOrNull { it.name.equals(request.nombreNegocio, ignoreCase = true) }"
)

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'w') as f:
    f.write(content)
