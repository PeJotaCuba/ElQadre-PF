package com.example.ui.screens.inicio

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
import androidx.compose.runtime.*
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
    private const val KEY_INICIO_GUIDE_SHOWN = "inicio_guide_shown_v1"
    private const val KEY_LOGIN_GUIDE_SHOWN = "login_guide_shown_v1"

    fun isInicioGuideShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INICIO_GUIDE_SHOWN, false)
    }

    fun setInicioGuideShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INICIO_GUIDE_SHOWN, true).apply()
    }

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
fun InicioGuiadoDialog(
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
                // Header
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(ElQadreNavySoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Bienvenido a ElQadre",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Guía rápida para comenzar a utilizar la aplicación en este dispositivo.",
                    fontSize = 13.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Divider(color = Slate200)

                // Step 1: PRUEBA GRATIS
                GuideStepItem(
                    stepNumber = "1",
                    icon = Icons.Outlined.VerifiedUser,
                    iconTint = ElQadreNavy,
                    title = "PRUEBA GRATIS",
                    description = "Pulse el botón PRUEBA GRATIS para solicitar una prueba gratuita de 7 días mediante SMS."
                )

                // Step 2: Esperar y Actualizar
                GuideStepItem(
                    stepNumber = "2",
                    icon = Icons.Outlined.Sync,
                    iconTint = ElQadreGoldDark,
                    title = "ACTUALIZAR",
                    description = "Una vez aprobada su solicitud, pulse el botón ACTUALIZAR para comprobar la autorización de su dispositivo."
                )

                // Step 3: Iniciar Sesión
                GuideStepItem(
                    stepNumber = "3",
                    icon = Icons.Outlined.Login,
                    iconTint = Emerald600,
                    title = "INICIAR SESIÓN",
                    description = "Al activarse la prueba, el botón cambiará automáticamente a INICIAR SESIÓN para ingresar al sistema."
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Flujo: Solicitar prueba gratis → Esperar → Pulsar ACTUALIZAR → Acceder.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElQadreNavy
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("inicio_guiado_entendido_button")
                ) {
                    Text("ENTENDIDO", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
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

                Divider(color = Slate200)

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
                    title = "SELECCIONAR USUARIO Y CONTRASEÑA",
                    description = "Elija su usuario de la lista, introduzca su contraseña y pulse Iniciar Sesión."
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreGoldSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Amber800,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Recuerde: Primero ACTUALIZAR, luego ingresar credenciales.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber800
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_guiado_entendido_button")
                ) {
                    Text("ENTENDIDO", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
        Surface(
            shape = CircleShape,
            color = Slate100,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$stepNumber. $title",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Slate600,
                lineHeight = 17.sp
            )
        }
    }
}
