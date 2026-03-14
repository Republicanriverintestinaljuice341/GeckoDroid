package com.adaptix.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adaptix.client.ui.theme.*

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    confirmColor: @Composable () -> androidx.compose.ui.graphics.Color = { RedError },
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor()),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmText, color = TextPrimary, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = TextMuted, fontSize = 13.sp)
            }
        }
    )
}
