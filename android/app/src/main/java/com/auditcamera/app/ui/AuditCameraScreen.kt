package com.auditcamera.app.ui

import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

/**
 * 撮影画面(横持ち固定)。
 *
 * 左: カメラのライブ映像 / 右: 項目ボタンと〇×▲シャッター。
 * 外部のカメラアプリは起動しないので、撮影後の確認画面は出ない(＝2工程のまま)。
 */
@Composable
fun AuditCameraScreen(
    storeName: String,
    categories: List<String>,
    selectedCategory: String,
    pendingCount: Int,
    shutterSoundEnabled: Boolean,
    onSelectCategory: (String) -> Unit,
    onEditStore: () -> Unit,
    onOpenReview: () -> Unit,
    newPhotoId: () -> String,
    pendingFileFor: (String) -> File,
    onCaptured: (photoId: String, category: String, judgement: String, file: File, capturedAt: Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // カメラ本体。ピンチでズーム、タップでピント合わせが標準で有効になっている
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            // 撮影の速さを優先(JPEG品質95相当。Web版の toDataURL(0.95) と同等)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            isPinchToZoomEnabled = true
            isTapToFocusEnabled = true
        }
    }

    LaunchedEffect(Unit) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    // シャッター音(自前の短い電子音。端末のメディア音量で調整できる)
    val shutterSound = remember { ShutterSound() }
    DisposableEffect(Unit) {
        onDispose { shutterSound.release() }
    }

    // 撮影時の白フラッシュ演出
    var flashing by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(if (flashing) 0.75f else 0f, label = "flash")
    LaunchedEffect(flashing) {
        if (flashing) {
            delay(120)
            flashing = false
        }
    }

    fun capture(judgement: String) {
        if (storeName.isBlank()) {
            Toast.makeText(context, "最初に店舗名を入力してください。", Toast.LENGTH_SHORT).show()
            return
        }

        // シャッターを押した瞬間の値を確定させる(連写しても混ざらないように)
        val photoId = newPhotoId()
        val category = selectedCategory
        val capturedAt = System.currentTimeMillis()
        val file = pendingFileFor(photoId)

        flashing = true
        if (shutterSoundEnabled) shutterSound.play()

        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        cameraController.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onCaptured(photoId, category, judgement, file, capturedAt)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        context,
                        "撮影に失敗しました。もう一度撮影してください。",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 画面幅に応じて操作パネルの幅と文字サイズを変える。
        // Galaxyの折りたたみ機のように、たたんだ状態で横幅が狭いときでも
        // ボタンが極端に小さくならないようにするため。
        val narrow = maxWidth < 700.dp
        val panelWidth = (maxWidth * 0.42f).coerceIn(240.dp, 320.dp)
        val categoryFontSize = if (narrow) 12.sp else 13.sp
        val shutterFontSize = if (narrow) 28.sp else 34.sp

    Row(modifier = Modifier.fillMaxSize()) {

        // ---- 左: カメラ映像 ----
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                    }
                }
            )

            // 撮影時の白フラッシュ
            if (flashAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(flashAlpha)
                        .background(Color.White)
                )
            }
        }

        // ---- 右: 操作パネル ----
        Column(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .background(Color(0xFF1C1C1C))
                .padding(if (narrow) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 店舗名は表示だけ。タップすると入力画面に戻れる
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditStore() }
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = storeName.ifBlank { "店舗名 未設定" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("変更", color = Color(0xFF8AB4F8), fontSize = 12.sp)
            }

            // 監査項目(タップしても撮影はされない。選択状態が変わるだけ)
            categories.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { category ->
                        val active = category == selectedCategory
                        Button(
                            onClick = { onSelectCategory(category) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) Color(0xFF0B5FBF) else Color(0xFF3A3A3A)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(category, fontSize = categoryFontSize, color = Color.White)
                        }
                    }
                }
            }

            // シャッター(3種類の判定)
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShutterButton("〇", Color(0xFF1E8449), shutterFontSize, Modifier.weight(1f)) { capture("〇") }
                ShutterButton("×", Color(0xFFC0392B), shutterFontSize, Modifier.weight(1f)) { capture("×") }
                ShutterButton("▲", Color(0xFFD68910), shutterFontSize, Modifier.weight(1f)) { capture("▲") }
            }

            // 送信状況＋見返し
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pendingCount == 0) "✓ 送信済み" else "送信待ち $pendingCount 件",
                    color = if (pendingCount == 0) Color(0xFF28A745) else Color(0xFFFF9500),
                    fontSize = 13.sp
                )
                Text(
                    text = "写真を見る",
                    color = Color(0xFF8AB4F8),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onOpenReview() }
                )
            }
        }
    }
    }
}

@Composable
private fun ShutterButton(
    label: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Text(label, fontSize = fontSize, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
