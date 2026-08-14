package com.auditcamera.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * シャッター音。
 *
 * Androidに用意されている「カメラのシャッター音」(MediaActionSound)は
 * 音量が固定で、端末の音量ボタンでも小さくできない。
 * そこで自前で短い電子音を鳴らすようにして、音量調整とオンオフを効くようにしている
 * (Web版がWebAudioで音を作っていたのと同じ考え方)。
 */
class ShutterSound {

    private var toneGenerator: ToneGenerator? = null

    private fun generator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                // メディア音量に連動させる。端末の音量ボタンで調整できる
                ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            }.getOrElse {
                Log.w(TAG, "シャッター音を用意できませんでした: ${it.message}")
                null
            }
        }
        return toneGenerator
    }

    /** 撮影音を鳴らす。失敗しても撮影自体には影響させない */
    fun play() {
        runCatching {
            generator()?.startTone(ToneGenerator.TONE_PROP_BEEP, DURATION_MS)
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    companion object {
        private const val TAG = "ShutterSound"

        /** 0〜100。メディア音量に対する相対的な大きさ */
        private const val VOLUME = 70

        /** 鳴らす長さ(ミリ秒)。短くしてテンポを損なわないようにする */
        private const val DURATION_MS = 80
    }
}
