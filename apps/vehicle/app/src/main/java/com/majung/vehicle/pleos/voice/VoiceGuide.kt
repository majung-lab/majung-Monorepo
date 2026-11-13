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

/** 운전자가 가족에게 말로 보내는 짧은 알림 (서버 driverMessage.kind) */
data class DriverMessage(val kind: String, val minutes: Int? = null) {
    /** 확인용으로 읽어 줄 말 */
    val words get() = when (kind) { "LATE" -> minutes?.let { "${it}분쯤 늦는다고" } ?: "조금 늦는다고"; "ALMOST" -> "거의 다 왔다고"; else -> "안에서 기다려 달라고" }
}

private val KOREAN_NUMBERS = mapOf("한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9, "일" to 1, "이" to 2, "삼" to 3, "사" to 4, "오" to 5, "육" to 6, "칠" to 7, "팔" to 8, "구" to 9)

/** "10분", "십오 분", "이십분", "오 분"처럼 들린 분을 숫자로. 없으면 null */
fun parseMinutes(heard: String): Int? {
    val s = heard.replace(" ", "")
    Regex("(\\d{1,3})분").find(s)?.let { return it.groupValues[1].toInt().takeIf { n -> n in 1..120 } }
    val m = Regex("([가-힣]{1,4})분").find(s)?.groupValues?.get(1) ?: return null
    // 끝에서부터 가장 긴 숫자 말을 찾는다 ("이십오" → 25, "십" → 10, "다섯" → 5)
    for (start in m.indices) {
        val w = m.substring(start)
        val n = when {
            w.contains("십") -> { val (tens, ones) = w.split("십", limit = 2); (if (tens.isEmpty()) 1 else KOREAN_NUMBERS[tens] ?: continue) * 10 + (if (ones.isEmpty()) 0 else KOREAN_NUMBERS[ones] ?: continue) }
            else -> KOREAN_NUMBERS[w] ?: continue
        }
        return n.takeIf { it in 1..120 }
    }
    return null
}

/** 운전자가 한 말을 알림으로. 알아듣지 못하면 null */
fun parseDriverMessage(heard: String): DriverMessage? {
    val s = heard.replace(" ", "")
    if (s.isEmpty()) return null
    if ("늦" in s) return DriverMessage("LATE", parseMinutes(s))
    if (listOf("안에서", "실내", "기다려", "나오지마").any { it in s }) return DriverMessage("WAIT_INSIDE")
    if (listOf("거의", "다왔", "곧도착", "금방", "곧가").any { it in s }) return DriverMessage("ALMOST")
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
    /** 듣고 있는 질문. 들은 말(못 들으면 "")을 한 번 받는다 */
    private var onAnswer: ((String) -> Unit)? = null
    /** 지금 묻는 까닭 (제안 답·운전자 알림). 다른 까닭의 cancel로 끊기지 않게 한다 */
    var purpose: String? = null; private set
    private var listenAfterSpeech = false
    var onState: (State) -> Unit = {}

    init {
        tts.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
            override fun onSuccess() = main.post { tts.setLocale(Locale.KOREA); Log.i(TAG, "tts ready"); ttsReady = true; waiting?.let { waiting = null; tts.speak(it, true) } }.let {}
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

    /** 엔진이 준비되기 전에는 말이 사라지고 끝났다는 신호도 오지 않는다. 마지막 말 하나를 들고 있다가 준비되면 말한다 */
    private var ttsReady = false
    private var waiting: String? = null
    private fun speak(text: String) { if (ttsReady) tts.speak(text, true) else waiting = text }

    /** 안내만 한다 */
    fun say(text: String) {
        listenAfterSpeech = false
        state(State.Speaking)
        speak(text)
    }

    /** 묻고 나서 한 번 듣는다. 답은 한 번만 온다 */
    fun ask(text: String, answer: (Boolean?) -> Unit) = listen(text, PROPOSAL) { answer(parseAnswer(it)) }

    /** 묻고 나서 들은 말을 그대로 한 번 넘긴다 (못 들으면 "") */
    fun listen(text: String, why: String, heard: (String) -> Unit) {
        onAnswer = heard; purpose = why
        listenAfterSpeech = true
        state(State.Speaking)
        speak(text)
    }

    /** 답을 기다리던 질문을 없앤다 (화면에서 먼저 답했을 때). why를 주면 그 까닭의 질문일 때만 */
    fun cancel(why: String? = null) {
        if (onAnswer == null || (why != null && why != purpose)) return
        onAnswer = null; purpose = null; listenAfterSpeech = false; waiting = null
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
        onAnswer = null; purpose = null
        main.removeCallbacks(giveUp)
        Log.i(TAG, "heard '$text'")
        state(State.Heard(text))
        answer(text)
    }

    private fun state(s: State) { onState(s) }

    fun release() { runCatching { stt.release() }; runCatching { tts.release() } }

    companion object {
        const val PROPOSAL = "proposal"
        const val DRIVER_MESSAGE = "driverMessage"
        private const val TAG = "VoiceGuide"
        private const val LISTEN_MS = 8_000L
    }
}
