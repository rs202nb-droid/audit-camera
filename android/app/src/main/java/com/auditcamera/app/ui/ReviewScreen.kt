package com.auditcamera.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auditcamera.app.data.PendingPhoto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 撮影した写真の見返し・削除画面。
 *
 * 送信前(送信待ち)の写真を消せば、クラウド側にも上がらない。
 * 送信済みの写真を消した場合は端末内のコピーだけが消える(ドライブ側には残る)。
 */
@Composable
fun ReviewScreen(
    photos: List<PendingPhoto>,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    var confirmTarget by remember { mutableStateOf<PendingPhoto?>(null) }
    val timeFormat = remember { SimpleDateFormat("M/d HH:mm", Locale.JAPAN) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)).padding(12.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("撮影した写真 (${photos.size}件)", color = Color.White, fontSize = 16.sp)
            TextButton(onClick = onBack) {
                Text("撮影に戻る", color = Color(0xFF8AB4F8))
            }
        }

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("まだ写真がありません", color = Color(0xFF888888))
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photos, key = { it.photoId }) { photo ->
                Column(
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    // 送信が済んだ写真はアプリ内の作業用ファイルを消しているため、
                    // その場合はギャラリー(監査カメラアルバム)のコピーを表示する
                    val thumbnailModel = remember(photo.localFilePath, photo.backupMediaUri) {
                        val localFile = File(photo.localFilePath)
                        if (localFile.exists()) localFile
                        else photo.backupMediaUri?.let { Uri.parse(it) }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbnailModel != null) {
                            AsyncImage(
                                model = thumbnailModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // 送信済みでギャラリーのコピーも消えている(30日経過後など)
                            Text("画像なし", color = Color(0xFF777777), fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${photo.judgement} ${photo.category}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = timeFormat.format(Date(photo.capturedAtMillis)),
                            color = Color(0xFF999999),
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val synced = photo.syncedAtMillis != null
                        Text(
                            text = if (synced) "送信済み" else "送信待ち",
                            color = if (synced) Color(0xFF28A745) else Color(0xFFFF9500),
                            fontSize = 11.sp
                        )
                        TextButton(onClick = { confirmTarget = photo }) {
                            Text("削除", color = Color(0xFFE57373), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        val synced = target.syncedAtMillis != null
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("この写真を削除しますか？") },
            text = {
                Text(
                    if (synced) {
                        "この写真は既に送信済みです。端末内のコピーは消えますが、" +
                            "ドライブ側には残ります。"
                    } else {
                        "まだ送信していないため、削除するとドライブにも上がりません。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.photoId)
                    confirmTarget = null
                }) { Text("削除する", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("やめる") }
            }
        )
    }
}
