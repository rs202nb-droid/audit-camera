package com.auditcamera.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 起動時の店舗名入力画面。
 * 撮影画面には入力欄を置かないため、店舗名の入力・変更はここで行う。
 */
@Composable
fun StoreSetupScreen(
    storeName: String,
    pendingCount: Int,
    shutterSoundEnabled: Boolean,
    onStoreNameChange: (String) -> Unit,
    onShutterSoundChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onOpenReview: () -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("監査カメラ", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Text(
            text = "監査日: $today",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA),
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = storeName,
            onValueChange = onStoreNameChange,
            label = { Text("店舗名") },
            placeholder = { Text("例: 渋谷店") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        )

        // シャッター音のオンオフ。静かな店内で切れるようにしている
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = shutterSoundEnabled, onCheckedChange = onShutterSoundChange)
            Text(
                text = "シャッター音を鳴らす",
                fontSize = 14.sp,
                modifier = Modifier.clickable { onShutterSoundChange(!shutterSoundEnabled) }
            )
        }

        Text(
            text = "音の大きさは端末の音量ボタン(メディア音量)で調整できます",
            fontSize = 11.sp,
            color = Color(0xFF888888),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(start = 12.dp)
        )

        Button(
            onClick = onStart,
            enabled = storeName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B5FBF)),
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Text("撮影を開始", fontSize = 18.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        if (pendingCount > 0) {
            Text(
                text = "送信待ち $pendingCount 件",
                color = Color(0xFFFF9500),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        TextButton(onClick = onOpenReview, modifier = Modifier.padding(top = 4.dp)) {
            Text("撮影した写真を見る", color = Color(0xFF8AB4F8))
        }
    }
}
