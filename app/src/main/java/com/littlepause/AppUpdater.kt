package com.littlepause

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val UpdateRose = Color(0xFFB8758D)
private val UpdateInk = Color(0xFF705A61)
private val UpdateMuted = Color(0xFFB49AA3)
private val UpdatePaper = Color(0xFFFFFFFF)

@Composable
fun UpdateCard() {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = UpdatePaper),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("um cantinho sempre em dia  ♡", color = UpdateInk)
            Text(
                "quando quiser, confira as novidades e baixe a versão mais recente pelo GitHub.",
                color = UpdateMuted
            )
            Button(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/sorodeleite/little-pause/releases/latest")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpdateRose)
            ) {
                Text("ver atualizações", color = Color.White)
            }
        }
    }
}
