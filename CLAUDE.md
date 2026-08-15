# audit-camera

店舗監査で証拠写真を撮り、Googleドライブとスプレッドシートへ自動保存するアプリ。
**Web版(index.html)** と **Androidネイティブ版(android/)** の2つが同じGASバックエンドを共有している。

## 構成

| | Web版 | Android版 |
|---|---|---|
| 場所 | `index.html`(単一ファイル) | `android/`(Kotlin + Compose) |
| 配信 | GitHub Pages(pushで自動反映) | APKをGitHub Releasesから配布 |
| カメラ | getUserMedia | CameraX(アプリ内プレビュー) |
| キュー | IndexedDB | Room |
| 送信 | ページ内のsetTimeoutループ | WorkManager(アプリ終了中も動く) |

Android版が本命。Web版は移行期の予備として残している。

## バックエンド(Google側。リポジトリにコードは無い)

- GAS Webアプリが `doPost` で JSON を受け、ドライブに画像・スプレッドシートに行を追加
- 送信ペイロード: `{ photoId, storeName, category, judgement, image, timestamp, token }`
  - `image` は `data:image/jpeg;base64,...` 形式(Web版の `canvas.toDataURL()` と同形式)
  - `token` は合言葉。素のURL叩きを弾く簡易対策で、完全な秘密ではない
- 応答: 成功 `{result:'success'}` / 失敗 `{result:'error', message}`
- GAS_URL と AUTH_TOKEN は Web版は index.html に直書き、Android版は `app/build.gradle.kts` の `buildConfigField`。**変更時は両方＋GAS側を同時に更新**すること

## 絶対に壊してはいけない契約

ドライブの保存構造とファイル名は、デスクトップのPythonツール2本
(`C:\Users\user\Desktop\2026業務監査\画像貼付アプリ\`)が直接読んでいる。

- フォルダ: `店舗名 / YYYYMMDD /`(日付は**8桁数字のみ**。`監査画像ダウンロードツール.py` が正規表現 `^(\d{4})(\d{2})(\d{2})$` で照合)
- ファイル名: 判定記号 `〇` `×` `▲` のいずれかを含み、最初の `_` より前がキャプションになる
  (`監査画像貼付ツール.py` の `get_caption()`)

この規則を変えるとPythonツールが動かなくなる。変える場合はツール側も同時に直すこと。

## 地雷リスト

- **送信ジョブの同時実行 → ドライブに日付フォルダが二重作成される**。GASが「フォルダを探す→無ければ作る」をやるため、同時にリクエストが飛ぶと両方が作ってしまう。`UploadWorker` の `uploadMutex` で送信を直列化して対策済み。**並列送信で速度を上げようとしないこと**
- **外部カメラアプリを起動する方式(`ACTION_IMAGE_CAPTURE` 等)は使わない**。撮影のたびに確認画面が挟まり操作工程が増える。過去にiPhone+AppSheetでこれをやって使い物にならなかった。アプリ内にCameraXでプレビューを埋め込む現在の方式を維持すること
- **撮影は「項目タップ → 〇×▲シャッター」の2工程を厳守**。ここに確認や入力を足さない
- **判定記号(〇×▲)をアプリから変更できるようにしない**。Pythonツール2本がこの記号をコードに直書きして
  写真を探しているため。監査項目の名前は設定画面から変更可(`_` や `\ / : * ? " < > |` は入力時に弾いている)
- **GASが success を返す前に写真を消さない**。証拠写真が消える
- **photoId は撮影時に1回だけ発行**し、再送時も同じ値を使う(GAS側の二重保存防止)
- **CameraXは画像を回さず、向き情報(EXIF)だけを付けて保存する**。ギャラリーやドライブは正しく表示するが、
  Excelへ貼るときのPillowはこの情報を無視するため写真が90度回る。`ImageOrientationFixer` で撮影直後に
  画像自体を回転させて対策済み。Python側も `ImageOps.exif_transpose()` で二重に保険をかけている
- **シャッター音に `MediaActionSound` を使わない**。音量固定で端末の音量ボタンを受け付けない。
  `ShutterSound`(ToneGenerator + STREAM_MUSIC)に置き換え済みで、音量調整とオンオフができる
- **見返し画面のサムネイルはローカルファイルだけを見ない**。送信済みの写真は作業用ファイルを削除するため、
  ギャラリー(`backupMediaUri`)へフォールバックする必要がある

## 開発環境(Android版)

このPCにセットアップ済み。Android StudioのGUIは使っていない。

- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Android SDK: `C:\Android`(platform-tools / android-34 / build-tools 34.0.0)
- ビルド: `android/` で `.\gradlew.bat assembleDebug`
- 配布: `gh release create <タグ> "android/app/build/outputs/apk/debug/app-debug.apk" ...` してスマホのChromeからDL
  - GitHubへのリリース作成はClaudeの権限では実行できないため、ユーザーが手動で流す
  - 初回インストール時、Google Playプロテクトがブロックする → 「詳細」→「インストールする」で回避

## 現在の状態(2026-08-14)

Android版は実装完了・実機検証済み(最新 v8)。実測で確認できたこと:

- 2工程の撮影フロー、確認画面なし、ピンチズーム・ピント合わせが動作
- ギャラリーの専用アルバム「監査カメラ」に保存(普段の写真と混ざらない)
- **機内モードで撮影 → アプリ完全終了 → 電波復帰 → アプリを開かないまま全件自動送信**を実機確認
- ネイティブ版で撮った写真に対し、Pythonツール2本が無改修で動作することを確認
- 画像の向きがギャラリー・ドライブ・Excelのすべてで正しいことを確認
- シャッター音のオンオフと音量調整が効くことを確認(システム強制音ではなかった)
- 監査項目の名前を設定画面から変更でき、ドライブのファイル名とExcelのキャプションに反映されることを確認

デスクトップの `監査画像貼付ツール.py` は、サムネイルを見て貼る写真を選べる版に差し替え済み
(差し替え前は `監査画像貼付ツール_v1_backup_20260814.py` として同じフォルダに保管)。

## 残課題

`backlog.md` を参照。
