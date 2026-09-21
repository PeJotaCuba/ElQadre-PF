package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ElQadreGold
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate600

/**
 * Hook para seleccionar un número de teléfono desde los contactos del dispositivo.
 * Solicita automáticamente el permiso READ_CONTACTS en tiempo de ejecución si aún no está otorgado.
 */
@Composable
fun rememberContactPicker(
    onContactPicked: (name: String, phone: String) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val rawPhone = if (numIdx != -1) it.getString(numIdx) ?: "" else ""
                        val name = if (nameIdx != -1) it.getString(nameIdx) ?: "" else ""

                        val cleanPhone = rawPhone.replace(" ", "")
                            .replace("-", "")
                            .replace("(", "")
                            .replace(")", "")
                            .trim()

                        onContactPicked(name, cleanPhone)
                    }
                }
            } catch (e: Exception) {
                // Intento alternativo consultando la tabla Phone
                try {
                    val cursor = context.contentResolver.query(contactUri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            val name = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                            if (idIdx != -1) {
                                val contactId = c.getString(idIdx)
                                val phoneCursor = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null
                                )
                                phoneCursor?.use { pCursor ->
                                    if (pCursor.moveToFirst()) {
                                        val pNumIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (pNumIdx != -1) {
                                            val phone = pCursor.getString(pNumIdx) ?: ""
                                            val cleanPhone = phone.replace(" ", "")
                                                .replace("-", "")
                                                .replace("(", "")
                                                .replace(")", "")
                                                .trim()
                                            onContactPicked(name, cleanPhone)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    contactPickerLauncher.launch(intent)
                } catch (e2: Exception) {
                    Toast.makeText(context, "No se pudo abrir la libreta de contactos.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Se requiere permiso para acceder a los contactos del dispositivo.", Toast.LENGTH_SHORT).show()
        }
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    contactPickerLauncher.launch(intent)
                } catch (e2: Exception) {
                    Toast.makeText(context, "No se pudo abrir la libreta de contactos.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

/**
 * Botón de icono para buscar contacto e insertarlo en el campo de texto.
 */
@Composable
fun ContactPickerIconButton(
    modifier: Modifier = Modifier,
    tint: Color = ElQadreNavy,
    onContactPicked: (name: String, phone: String) -> Unit
) {
    val pickContact = rememberContactPicker(onContactPicked)

    IconButton(
        onClick = pickContact,
        modifier = modifier.size(40.dp).testTag("btn_pick_contact"),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint)
    ) {
        Icon(
            imageVector = Icons.Filled.Contacts,
            contentDescription = "Buscar número en Contactos",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
