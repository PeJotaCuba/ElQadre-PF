import re

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminDashboardScreen.kt', 'r') as f:
    content = f.read()

# Replace the General SMS Tracker & Activation Requests Card and LazyColumn.
# This starts at "// General SMS Tracker & Activation Requests" (around line 363)
# and ends at the closing brace of the LazyColumn (around line 490) or the `requests` variable used for it.
# We will just find "// General SMS Tracker & Activation Requests" up to `// Process Dialog`

start_marker = "// General SMS Tracker & Activation Requests"
end_marker = "// Process Dialog"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx != -1 and end_idx != -1:
    new_section = """// Dedicated Card for Activation SMS
        SuperAdminActivationSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshRequests()
            }
        )
        
        // Dedicated Card for Usuarios SMS
        SuperAdminUsuariosSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshRequests()
            }
        )
        
    """
    
    content = content[:start_idx] + new_section + content[end_idx:]

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminDashboardScreen.kt', 'w') as f:
    f.write(content)

