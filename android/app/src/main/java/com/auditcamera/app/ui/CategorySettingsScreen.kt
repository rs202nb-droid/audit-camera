package com.auditcamera.app.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 監査項目の名前を変更する設定画面。
 *
 * 判定ボタン(〇×▲)は変更できない。この記号はデスクトップのPythonツール2本が
 * ファイル名から写真を探すときの手がかりとして使っており、
 * 変えるとExcelへの貼り付けと月次ダウンロードが動かなくなるため。
 */
@Composable
fun CategorySettingsScreen(
    categories: List<String>,
    onSave: (List<String>) -> Unit,
    onResetToDefault: () -> Unit,
    onBack: () -> Unit
) {
    // 編集中の値。保存を押すまで実際の設定には反映しない
    val editing = remember(categories) { mutableStateListOf<String>().apply { addAll(categories) } }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("監査項目の設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Text(
            text = "撮影画面のボタンに表示される名前です",
            fontSize = 12.sp,
            color = Color(0xFF999999),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        editing.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = {
                    editing[index] = it
                    errorMessage = null
                },
                label = { Text("項目 ${index + 1}") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .padding(bottom = 8.dp)
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFE57373),
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Text(
            text = "※ 判定ボタン(〇×▲)は変更できません。この記号はExcelへ貼り付けるPythonツールが" +
                "写真を探すときの目印として使っているためです。",
            fontSize = 11.sp,
            color = Color(0xFF888888),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val trimmed = editing.map { it.trim() }
                    val problem = validate(trimmed)
                    if (problem != null) {
                        errorMessage = problem
                    } else {
                        onSave(trimmed)
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B5FBF)),
                modifier = Modifier.weight(1f)
            ) {
                Text("保存する", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("やめる", color = Color(0xFFAAAAAA))
            }
        }

        TextButton(
            onClick = {
                onResetToDefault()
                onBack()
            },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("最初の項目名に戻す", color = Color(0xFF8AB4F8), fontSize = 13.sp)
        }
    }
}

/** 入力内容を確認する。問題があればその説明を返す */
private fun validate(categories: List<String>): String? {
    if (categories.any { it.isBlank() }) {
        return "空欄の項目があります。すべて入力してください。"
    }
    // ファイル名の「最初の_より前をキャプションにする」規則を壊さないため
    if (categories.any { it.contains("_") }) {
        return "項目名にアンダースコア( _ )は使えません。Excelのキャプションが正しく出なくなります。"
    }
    // ドライブのファイル名に使えない文字
    if (categories.any { it.contains(Regex("[\\\\/:*?\"<>|]")) }) {
        return "項目名に次の記号は使えません: \\ / : * ? \" < > |"
    }
    if (categories.distinct().size != categories.size) {
        return "同じ名前の項目があります。区別できる名前にしてください。"
    }
    return null
}
