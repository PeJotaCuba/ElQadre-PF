with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'r') as f:
    content = f.read()

# buildAutorizacionSms
old_build_auth = """    fun buildAutorizacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        ciudad: String = ""
    ): String {"""

new_build_auth = """    fun buildAutorizacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        dvc: String = "",
        ciudad: String = ""
    ): String {"""
content = content.replace(old_build_auth, new_build_auth)

old_build_auth_body = """        builder.append("MP: ${movilPrincipal.trim()}\\n")
        builder.append("MA: ${movilAlt.trim()}\\n")
        builder.append("Ciudad: ${ciudad.trim()}")"""
new_build_auth_body = """        builder.append("MP: ${movilPrincipal.trim()}\\n")
        builder.append("MA: ${movilAlt.trim()}\\n")
        if (dvc.isNotBlank()) builder.append("DVC: ${if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"}\\n")
        builder.append("Ciudad: ${ciudad.trim()}")"""
content = content.replace(old_build_auth_body, new_build_auth_body)

# buildAutorizacionSms compat overload
old_compat = """    fun buildAutorizacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildAutorizacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            ciudad = ""
        )
    }"""
new_compat = """    fun buildAutorizacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildAutorizacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            dvc = dvc,
            ciudad = ""
        )
    }"""
content = content.replace(old_compat, new_compat)


# buildActivacionSms
old_build_act = """    fun buildActivacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        ciudad: String = ""
    ): String {"""

new_build_act = """    fun buildActivacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        dvc: String = "",
        ciudad: String = ""
    ): String {"""
content = content.replace(old_build_act, new_build_act)

old_build_act_body = """        builder.append("MP: ${movilPrincipal.trim()}\\n")
        builder.append("MA: ${movilAlt.trim()}\\n")
        builder.append("Ciudad: ${ciudad.trim()}")"""
new_build_act_body = """        builder.append("MP: ${movilPrincipal.trim()}\\n")
        builder.append("MA: ${movilAlt.trim()}\\n")
        if (dvc.isNotBlank()) builder.append("DVC: ${if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"}\\n")
        builder.append("Ciudad: ${ciudad.trim()}")"""
content = content.replace(old_build_act_body, new_build_act_body)

# buildActivacionSms compat overload
old_compat_act = """    fun buildActivacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildActivacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            ciudad = ""
        )
    }"""
new_compat_act = """    fun buildActivacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildActivacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            dvc = dvc,
            ciudad = ""
        )
    }"""
content = content.replace(old_compat_act, new_compat_act)

with open('app/src/main/java/com/example/licensing/SuperAdminSmsHelper.kt', 'w') as f:
    f.write(content)

