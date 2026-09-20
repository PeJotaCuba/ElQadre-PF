with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'r') as f:
    content = f.read()

# find first 'package'
idx = content.find('package')
if idx != -1:
    content = content[idx:]

content = "import com.example.licensing.SuperAdminPaymentHelper\n" + content

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'w') as f:
    f.write(content)
