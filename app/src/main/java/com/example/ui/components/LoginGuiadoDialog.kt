package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

object GuiadoPrefsManager {
    private const val PREFS_NAME = "elqadre_guiado_prefs"
    private const val KEY_LOGIN_GUIDE_SHOWN = "login_guide_shown_v1"

    fun isLoginGuideShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOGIN_GUIDE_SHOWN, false)
    }

    fun setLoginGuideShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOGIN_GUIDE_SHOWN, true).apply()
    }
}

@Composable
fun LoginGuiadoDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(ElQadreGoldSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = ElQadreGoldDark,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Acceso al Sistema",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Siga este orden para iniciar sesión correctamente por primera vez:",
                    fontSize = 13.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Slate200)

                GuideStepItem(
                    stepNumber = "1",
                    icon = Icons.Outlined.Sync,
                    iconTint = ElQadreGoldDark,
                    title = "PULSAR ACTUALIZAR PRIMERO",
                    description = "Antes de introducir credenciales, pulse ACTUALIZAR para descargar y sincronizar la lista de usuarios y roles autorizados."
                )

                GuideStepItem(
                    stepNumber = "2",
                    icon = Icons.Outlined.Person,
                    iconTint = ElQadreNavy,
                    title = "INGRESAR USUARIO",
                    description = "Escriba su nombre de usuario exactamente como fue registrado por el Dueño."
                )

                GuideStepItem(
                    stepNumber = "3",
                    icon = Icons.Outlined.Key,
                    iconTint = ElQadreNavy,
                    title = "INGRESAR CONTRASEÑA",
                    description = "Introduzca su contraseña asignada. Si no la recuerda, use la opción de recuperar contraseña."
                )

                GuideStepItem(
                    stepNumber = "4",
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = Color(0xFF16A34A),
                    title = "PULSAR INGRESAR",
                    description = "Acceda a su pantalla correspondiente según su rol asignado."
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_entendido_login_guiado"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "ENTENDIDO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepItem(
    stepNumber: String,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(ElQadreGoldSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreGoldDark
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = Slate600,
                lineHeight = 16.sp
            )
        }
    }
}
