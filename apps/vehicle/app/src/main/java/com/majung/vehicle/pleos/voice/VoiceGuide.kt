package com.majung.vehicle.pleos.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

/** 말로 들은 답. 바꾸자는 말이면 true, 그대로 가자는 말이면 false, 알아듣지 못하면 null */
fun parseAnswer(heard: String): Boolean? {
    val s = heard.replace(" ", "")
    // "안 바꿔"에 "바꿔"가 들어 있으므로 거절 말을 먼저 본다
    if (listOf("그대로", "거절", "아니", "싫어", "안바꿔", "안돼", "괜찮아").any { it in s }) return false
    if (listOf("바꿔", "바꾸", "수락", "좋아", "그래", "응", "네", "오케이", "콜").any { it in s }) return true
    return null
}

/**
 * Pleos TextToSpeech·SpeechToText로 묻고 한 번 듣는다. 기기 안 엔진(ON_DEVICE)만 써서 앱 등록·네트워크가 없어도 된다.
 * 콜백은 모두 메인 스레드로 넘긴다.
 */
class VoiceGuide(context: Context) {
    sealed interface State {
        data object Idle : State
        data object Speaking : State
        data object Listening : State
        data class Heard(val text: String) : State
    }

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private var onAnswer: ((Boolean?) -> Unit)? = null
    private var listenAfterSpeech = false
    var onState: (State) -> Unit = {}

    init {
        tts.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
            override fun onSuccess() { tts.setLocale(Locale.KOREA); Log.i(TAG, "tts ready") }
            override fun onFailure(error: TtsError) { Log.w(TAG, "tts init $error") }
        })
        tts.addEventListener(object : EventListener {
            override fun onStart() {}
            override fun onReady() {}
            override fun onDone() = main.post { if (listenAfterSpeech) { listenAfterSpeech = false; listen() } else state(State.Idle) }.let {}
            override fun onError(error: TtsError) = main.post { Log.w(TAG, "tts $error"); if (listenAfterSpeech) { listenAfterSpeech = false; listen() } else state(State.Idle) }.let {}
            override fun onStop() {}
            override fun onUpdatedRms(rms: Double) {}
        })
        stt.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
            override fun onSuccess() { stt.setLocale(Locale.KOREA); Log.i(TAG, "stt ready") }
            override fun onFailure(error: SttError) { Log.w(TAG, "stt init $error") }
        })
        stt.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) { if (isFinal) main.post { heard(text) } }
            override fun onUpdatedEpdData(start: Long, end: Long) {}
            override fun onStartedRecognition() {}
            override fun onEndedRecognition() {}
            override fun onError(error: SttError) { main.post { Log.w(TAG, "stt $error"); heard("") } }
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}
        })
    }

    /** 안내만 한다 */
    fun say(text: String) {
        listenAfterSpeech = false
        state(State.Speaking)
        tts.speak(text, true)
    }

    /** 묻고 나서 한 번 듣는다. 답은 한 번만 온다 */
    fun ask(text: String, answer: (Boolean?) -> Unit) {
        onAnswer = answer
        listenAfterSpeech = true
        state(State.Speaking)
        tts.speak(text, true)
    }

    /** 답을 기다리던 질문을 없앤다 (화면에서 먼저 답했을 때) */
    fun cancel() {
        if (onAnswer == null) return
        onAnswer = null; listenAfterSpeech = false
        main.removeCallbacks(giveUp)
        runCatching { tts.stop() }; runCatching { stt.stop() }
        state(State.Idle)
    }

    // ponytail: 에뮬레이터 마이크에 말을 넣을 수 없어 디버그 빌드에서만 들은 말을 직접 넣는다 (MainActivity 방송)
    fun inject(text: String) { if (onAnswer != null) { runCatching { stt.stop() }; heard(text) } }

    /** 말이 없으면 이만큼 듣고 그만둔다 */
    private val giveUp = Runnable { if (onAnswer != null) { runCatching { stt.stop() }; heard("") } }

    private fun listen() {
        if (onAnswer == null) return
        state(State.Listening)
        main.postDelayed(giveUp, LISTEN_MS)
        runCatching { stt.request() }.onFailure { Log.w(TAG, "stt request", it); heard("") }
    }

    private fun heard(text: String) {
        val answer = onAnswer ?: return
        onAnswer = null
        main.removeCallbacks(giveUp)
        Log.i(TAG, "heard '$text'")
        state(State.Heard(text))
        answer(parseAnswer(text))
    }

    private fun state(s: State) { onState(s) }

    fun release() { runCatching { stt.release() }; runCatching { tts.release() } }

    private companion object { const val TAG = "VoiceGuide"; const val LISTEN_MS = 8_000L }
}
