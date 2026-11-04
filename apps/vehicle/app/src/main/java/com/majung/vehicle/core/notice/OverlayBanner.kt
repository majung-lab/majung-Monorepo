package com.majung.vehicle.core.notice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.majung.vehicle.MainActivity

/**
 * 내비가 앞에 있는 동안 화면 위쪽에 떠 있는 마중 배너.
 * - 내비 위쪽 가장자리에 붙어 있고, 방문이 끝날 때까지 떠 있다. 누르면 마중 화면으로 돌아온다.
 * - 위로 밀면 작은 "마중" 손잡이만 남고, 손잡이를 내리거나 누르면 다시 펼쳐진다.
 * - 가족 응답 같은 새 소식이 오면 접혀 있어도 다시 펼쳐진다.
 * - 마중 화면이 보이는 동안에는 숨는다.
 * 다른 앱 위에 그리기 권한이 없으면 아무것도 하지 않는다 (알림 목록의 알림만 남는다).
 */
object OverlayBanner {
    private var view: LinearLayout? = null
    private var title = ""
    private var text = ""
    private var highlight = false
    private var active = false
    private var collapsed = false
    private var appVisible = false

    /** 진행 중인 방문 상태 */
    fun update(context: Context, title: String, text: String) {
        active = true
        if (this.title != title || !highlight) { this.title = title; this.text = text }
        render(context)
    }

    /** 새 소식: 접어 뒀어도 다시 펼치고 강조한다 */
    fun alert(context: Context, title: String, text: String) {
        active = true
        this.title = title; this.text = text; highlight = true
        collapsed = false
        render(context)
    }

    fun setAppVisible(context: Context, visible: Boolean) {
        appVisible = visible
        // 마중을 보고 나면 강조한 소식은 읽은 것으로 본다
        if (visible) highlight = false
        render(context)
    }

    fun stop(context: Context) {
        active = false
        collapsed = false
        render(context)
    }

    private fun render(context: Context) {
        val shouldShow = active && !appVisible && Settings.canDrawOverlays(context)
        if (!shouldShow) return remove(context)
        val banner = view ?: create(context.applicationContext) ?: return
        (banner.getChildAt(0) as TextView).apply { text = title; visibility = if (collapsed) View.GONE else View.VISIBLE }
        (banner.getChildAt(1) as TextView).apply {
            text = "${this@OverlayBanner.text} · 위로 밀면 접기"
            setTextColor(if (highlight) ACCENT else SUB)
            visibility = if (collapsed) View.GONE else View.VISIBLE
        }
        banner.getChildAt(2).visibility = if (collapsed) View.VISIBLE else View.GONE
    }

    private fun setCollapsed(context: Context, value: Boolean) {
        collapsed = value
        render(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun create(context: Context): LinearLayout? {
        val scale = scale(context)
        fun px(dp: Int) = (dp * scale).toInt()
        val radius = px(12).toFloat()
        val banner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(10), px(18), px(12))
            // 위쪽은 내비 가장자리에 붙으므로 아래 모서리만 둥글게
            background = GradientDrawable().apply {
                setColor(BG); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius); setStroke(px(2), ACCENT)
            }
            addView(TextView(context).apply {
                setTextColor(TEXT); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20).toFloat())
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(15).toFloat())
                setPadding(0, px(3), 0, 0)
            })
            // 접었을 때 남는 손잡이
            addView(TextView(context).apply {
                text = "마중  ⌄"
                setTextColor(ACCENT); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16).toFloat())
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
            })
        }
        // 손가락을 따라 움직이지 않고, 손을 뗐을 때 방향만 보고 접기/펼치기를 바로 바꾼다
        var downY = 0f
        banner.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    when {
                        !collapsed && dy < -px(20) -> setCollapsed(context, true)
                        collapsed && (dy > px(12) || kotlin.math.abs(dy) < px(10)) -> setCollapsed(context, false)
                        !collapsed && kotlin.math.abs(dy) < px(10) -> openApp(context)
                    }
                    true
                }
                else -> true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 내비 위쪽 가장자리에 딱 붙인다. 오른쪽에 두어 내비의 길 안내(왼쪽 위)와 2D 버튼(오른쪽 끝)을 피한다
            gravity = Gravity.TOP or Gravity.END
            x = px(90); y = 0
        }
        return try {
            context.getSystemService(WindowManager::class.java).addView(banner, params)
            view = banner
            banner
        } catch (_: Exception) { null }
    }

    private fun remove(context: Context) {
        val banner = view ?: return
        view = null
        runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(banner) }
    }

    private fun openApp(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /** 앱 화면과 비슷한 크기로 보이게 전체 화면 폭 기준(1280dp)으로 키운다 (앱 창 폭이 아니라 화면 전체) */
    private fun scale(context: Context): Float {
        val density = context.resources.displayMetrics.density
        val widthPx = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.width()
        return density * (widthPx / density / 1280f).coerceAtLeast(1f)
    }

    private const val BG = 0xF00E1318.toInt()
    private const val TEXT = 0xFFFFFFFF.toInt()
    private const val SUB = 0xFFB4BCC6.toInt()
    private const val ACCENT = 0xFFF5A524.toInt()
}
