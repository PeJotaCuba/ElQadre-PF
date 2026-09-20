with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminDashboardScreen.kt', 'r') as f:
    content = f.read()

# Add rememberLauncherForActivityResult for Contacts
import_text = "import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport androidx.core.content.ContextCompat\n"

if "import android.Manifest" not in content:
    content = content.replace("package com.example.ui.screens.superadmin\n", "package com.example.ui.screens.superadmin\n" + import_text)

launcher_code = """
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) refreshRequests()
        }
    )
    
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
        refreshRequests()
    }
"""

old_launched = """    LaunchedEffect(Unit) {
        refreshRequests()
    }"""

content = content.replace(old_launched, launcher_code)

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminDashboardScreen.kt', 'w') as f:
    f.write(content)
