package com.auditcamera.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auditcamera.app.ui.AuditCameraScreen
import com.auditcamera.app.ui.MainViewModel
import com.auditcamera.app.ui.ReviewScreen
import com.auditcamera.app.ui.Screen
import com.auditcamera.app.ui.StoreSetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuditCameraApp()
                }
            }
        }
    }
}

@Composable
private fun AuditCameraApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current

    val screen by vm.screen.collectAsState()
    val storeName by vm.storeName.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val shutterSoundEnabled by vm.shutterSoundEnabled.collectAsState()
    val photos by vm.allPhotos.collectAsState()

    // カメラ権限。初回起動時に1回だけ確認する(写真1枚ごとには聞かれない)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (screen) {
        Screen.SETUP -> StoreSetupScreen(
            storeName = storeName,
            pendingCount = pendingCount,
            shutterSoundEnabled = shutterSoundEnabled,
            onStoreNameChange = vm::updateStoreName,
            onShutterSoundChange = vm::setShutterSoundEnabled,
            onStart = { vm.goTo(Screen.CAMERA) },
            onOpenReview = { vm.goTo(Screen.REVIEW) }
        )

        Screen.CAMERA -> {
            if (hasCameraPermission) {
                AuditCameraScreen(
                    storeName = storeName,
                    selectedCategory = selectedCategory,
                    pendingCount = pendingCount,
                    shutterSoundEnabled = shutterSoundEnabled,
                    onSelectCategory = vm::selectCategory,
                    onEditStore = { vm.goTo(Screen.SETUP) },
                    onOpenReview = { vm.goTo(Screen.REVIEW) },
                    newPhotoId = vm::newPhotoId,
                    pendingFileFor = vm::pendingFileFor,
                    onCaptured = vm::onPhotoCaptured
                )
            } else {
                // 権限を断られた場合の案内
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("カメラの使用を許可してください")
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("許可する") }
                    }
                }
            }
        }

        Screen.REVIEW -> ReviewScreen(
            photos = photos,
            onDelete = vm::deletePhoto,
            onBack = { vm.goTo(if (storeName.isBlank()) Screen.SETUP else Screen.CAMERA) }
        )
    }
}
