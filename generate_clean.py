import re

def process(filepath, card_type):
    with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminTrialSmsCard.kt', 'r') as f:
        content = f.read()

    # Truncate at TrialSmsReviewConfirmDialog
    idx = content.find("private fun TrialSmsReviewConfirmDialog")
    if idx != -1:
        # we need to find the `@Composable` right before it
        comp_idx = content.rfind("@Composable", 0, idx)
        if comp_idx != -1:
            content = content[:comp_idx]

    if card_type == 'ACTIVACION':
        content = content.replace("package com.example.ui.screens.superadmin", "package com.example.ui.screens.superadmin\nimport com.example.licensing.SuperAdminPaymentHelper")
        content = content.replace("SuperAdminTrialSmsCard", "SuperAdminActivationSmsCard")
        content = content.replace("TrialSmsRequest", "SuperAdminSmsRequest")
        content = content.replace("scanInboxForTrialRequests", 'scanInboxForRequests')
        content = content.replace("trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context)", 'trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context).filter { it.tipo == "ACTIVACION" }')
        content = content.replace("SOLICITUD DE PRUEBA (SMS)", "SOLICITUD DE ACTIVACIÓN")
        content = content.replace("Rastreo y procesamiento automático de solicitudes de 7 días", "Detección de pagos confirmados y generación de Q_licencias.json")
    elif card_type == 'USUARIOS':
        content = content.replace("SuperAdminTrialSmsCard", "SuperAdminUsuariosSmsCard")
        content = content.replace("TrialSmsRequest", "SuperAdminSmsRequest")
        content = content.replace("scanInboxForTrialRequests", 'scanInboxForRequests')
        content = content.replace("trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context)", 'trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context).filter { it.tipo == "NUEVO_USUARIO" }')
        content = content.replace("SOLICITUD DE PRUEBA (SMS)", "SM USUARIOS")
        content = content.replace("Rastreo y procesamiento automático de solicitudes de 7 días", "Validación de administradores y generación de Q_XXXusuarios.json")
        
    content = content.replace("movilPrincipal", "numeroMovil")
    content = content.replace("movilAlternativo", "numeroMovilAlt")

    old_review_dialog = """    // REVIEW & PROCESS CONFIRMATION DIALOG
    requestToReview?.let { req ->
        TrialSmsReviewConfirmDialog(
            request = req,
            onConfirm = { name, dueno, mp, ma, ciudad, dvc ->
                coroutineScope.launch {
                    val result = SuperAdminSmsHelper.processTrialRequest(
                        context = context,
                        request = req,
                        customName = name,
                        customDueno = dueno,
                        customMp = mp,
                        customMa = ma,
                        customCiudad = ciudad,
                        customDvc = dvc
                    )
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    successBusinessRecord = result.first
                    generatedFileToShare = result.second
                }
            },
            onDismiss = { requestToReview = null }
        )
    }"""
    
    if card_type == 'ACTIVACION':
        new_review_dialog = """    // REVIEW & PROCESS CONFIRMATION DIALOG
    requestToReview?.let { req ->
        ActivationProcessDialog(
            request = req,
            onApproved = { file ->
                coroutineScope.launch {
                    SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    generatedFileToShare = file
                }
            },
            onDismiss = { requestToReview = null }
        )
    }"""
    elif card_type == 'USUARIOS':
        new_review_dialog = """    // REVIEW & PROCESS CONFIRMATION DIALOG
    requestToReview?.let { req ->
        UsuariosProcessDialog(
            request = req,
            onApproved = { file ->
                coroutineScope.launch {
                    SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    generatedFileToShare = file
                }
            },
            onDismiss = { requestToReview = null }
        )
    }"""
        
    content = content.replace(old_review_dialog, new_review_dialog)
    
    old_success_overlay = """    // SUCCESS & SHARE OVERLAY
    if (successBusinessRecord != null && generatedFileToShare != null) {
        TrialSmsSuccessOverlayDialog(
            business = successBusinessRecord!!,
            file = generatedFileToShare!!,
            onSendConfirmationSms = {
                val smsText = "ELQADRE: Su solicitud de prueba para '${successBusinessRecord!!.name}' ha sido APROBADA por 7 dias. Codigo de negocio: ${successBusinessRecord!!.code}."
                SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, successBusinessRecord!!.phone, smsText)
            },
            onShareJson = {
                SuperAdminSmsHelper.shareJsonFile(context, generatedFileToShare!!, "Compartir Q_preuba.json")
            },
            onDismiss = {
                successBusinessRecord = null
                generatedFileToShare = null
            }
        )
    }"""
    
    new_success_overlay = """    // SUCCESS & SHARE OVERLAY
    generatedFileToShare?.let { file ->
        FileGeneratedSuccessDialogLocal(
            file = file,
            onShare = {
                SuperAdminSmsHelper.shareJsonFile(context, file, "Compartir JSON")
            },
            onDismiss = {
                generatedFileToShare = null
            }
        )
    }"""
    
    content = content.replace(old_success_overlay, new_success_overlay)
    
    with open(f'{card_type.lower()}_dialog.txt', 'r') as diag_f:
        diag_content = diag_f.read()
            
    content += diag_content

    content += """
@Composable
private fun FileGeneratedSuccessDialogLocal(
    file: java.io.File,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Archivo Generado Exitosamente") },
        text = { androidx.compose.material3.Text("Se ha guardado ${file.name}. ¿Desea compartirlo ahora?") },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onShare(); onDismiss() }) { androidx.compose.material3.Text("Compartir") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cerrar") }
        }
    )
}
"""

    with open(filepath, 'w') as f:
        f.write(content)

process('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'ACTIVACION')
process('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'USUARIOS')

