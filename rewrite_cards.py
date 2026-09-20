import os

def rewrite_card(filepath, card_type):
    with open(filepath, 'r') as f:
        content = f.read()
        
    if card_type == 'ACTIVACION':
        content = content.replace("package com.example.ui.screens.superadmin", "package com.example.ui.screens.superadmin\nimport com.example.licensing.SuperAdminPaymentHelper")
        content = content.replace("SuperAdminTrialSmsCard", "SuperAdminActivationSmsCard")
        content = content.replace("TrialSmsRequest", "SuperAdminSmsRequest")
        content = content.replace("scanInboxForTrialRequests(context)", 'scanInboxForRequests(context).filter { it.tipo == "ACTIVACION" }')
        content = content.replace("SOLICITUD DE PRUEBA (SMS)", "SOLICITUD DE ACTIVACIÓN")
        content = content.replace("Rastreo y procesamiento automático de solicitudes de 7 días", "Detección de pagos confirmados y generación de Q_licencias.json")
    elif card_type == 'USUARIOS':
        content = content.replace("SuperAdminTrialSmsCard", "SuperAdminUsuariosSmsCard")
        content = content.replace("TrialSmsRequest", "SuperAdminSmsRequest")
        content = content.replace("scanInboxForTrialRequests(context)", 'scanInboxForRequests(context).filter { it.tipo == "NUEVO_USUARIO" }')
        content = content.replace("SOLICITUD DE PRUEBA (SMS)", "SM USUARIOS")
        content = content.replace("Rastreo y procesamiento automático de solicitudes de 7 días", "Validación de administradores y generación de Q_XXXusuarios.json")
        
    # Replace movilPrincipal and movilAlternativo
    content = content.replace("movilPrincipal", "numeroMovil")
    content = content.replace("movilAlternativo", "numeroMovilAlt")

    # Replace Review Process Dialog usage
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
    
    # Replace success overlay usage
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
    
    # Remove TrialSmsReviewConfirmDialog definition completely
    idx_dialog = content.find("private fun TrialSmsReviewConfirmDialog(")
    if idx_dialog != -1:
        # Find the end of TrialSmsReviewConfirmDialog.
        # It's followed by `private fun EditRequestOverlayDialog`
        idx_next = content.find("private fun EditRequestOverlayDialog(", idx_dialog)
        # However, `EditRequestOverlayDialog` is needed. Wait, we don't need EditRequestOverlayDialog!
        # Because we don't edit requests, we just approve them.
        # But wait, does TrialSmsPendientesOverlayDialog use EditRequestOverlayDialog?
        # Let's check the code: TrialSmsPendientesOverlayDialog shows a list, and on tap it calls `onProcess(req)` which sets `requestToReview`. 
        # So we can just leave EditRequestOverlayDialog in case it's referenced, or delete it if it's not.
        # Let's just delete TrialSmsReviewConfirmDialog up to `private fun EditRequestOverlayDialog`
        
        # Actually we can just truncate from `TrialSmsReviewConfirmDialog` to EOF, then append our required dialogs and the remaining overlays!
        pass
        
    # Better yet, let's just replace the definition of `TrialSmsReviewConfirmDialog` with `ActivationProcessDialog` and `UsuariosProcessDialog` + `FileGeneratedSuccessDialogLocal`
    # Let's find `private fun TrialSmsReviewConfirmDialog`
    import_idx = content.find("private fun TrialSmsReviewConfirmDialog")
    # find where TrialSmsReviewConfirmDialog ends: it has `@Composable\nprivate fun EditRequestOverlayDialog` after it.
    end_dialog_idx = content.find("@Composable\nprivate fun EditRequestOverlayDialog", import_idx)
    
    if import_idx != -1 and end_dialog_idx != -1:
        # Cut it out
        prefix = content[:import_idx]
        suffix = content[end_dialog_idx:]
        
        # inject new dialogs
        with open(f'{card_type.lower()}_dialog.txt', 'r') as diag_f:
            diag_content = diag_f.read()
            
        content = prefix + diag_content + suffix
        
    # Fix remaining references to TrialSmsSuccessOverlayDialog inside suffix (actually we don't need TrialSmsSuccessOverlayDialog definition anymore)
    # Let's remove TrialSmsSuccessOverlayDialog definition
    succ_idx = content.find("private fun TrialSmsSuccessOverlayDialog")
    if succ_idx != -1:
        # find the end of it (it's the last function in the file)
        content = content[:succ_idx]
        
    # Add FileGeneratedSuccessDialogLocal at the end of the file
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

rewrite_card('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'ACTIVACION')
rewrite_card('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'USUARIOS')

