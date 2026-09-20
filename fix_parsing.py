import re

with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'r') as f:
    content = f.read()

# Add DVC extraction
dvc_case = """                upper.startsWith("DVC:") || upper.startsWith("DVC :") -> {
                    val rawVal = line.substringAfter(":").trim()
                    dvc = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }"""

old_ciudad_case = """                upper.startsWith("CIUDAD:") || upper.startsWith("CIUDAD :") || upper.startsWith("PROVINCIA:") || upper.startsWith("MUNICIPIO:") -> {"""

content = content.replace(old_ciudad_case, dvc_case + "\n" + old_ciudad_case)

with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'w') as f:
    f.write(content)
