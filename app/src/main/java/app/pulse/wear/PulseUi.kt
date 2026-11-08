package app.pulse.wear

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

internal object PulseColor {
    val background = Color.rgb(242, 242, 247)
    val surface = Color.WHITE
    val ink = Color.rgb(29, 29, 31)
    val muted = Color.rgb(97, 98, 111)
    val line = Color.rgb(231, 232, 239)
    val blue = Color.rgb(9, 110, 220)
    val paleBlue = Color.rgb(232, 242, 255)
    val green = Color.rgb(20, 113, 69)
    val paleGreen = Color.rgb(231, 246, 238)
    val amber = Color.rgb(126, 62, 12)
    val paleAmber = Color.rgb(255, 242, 222)
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.rounded(fill: Int, radius: Int, border: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        border?.let { setStroke(dp(1), it) }
    }

internal class PulseUi(private val context: Context) {
    fun column(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun text(value: String, size: Int, color: Int = PulseColor.ink, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun card(padding: Int = 18): LinearLayout = column().apply {
        background = context.rounded(PulseColor.surface, 24, PulseColor.line)
        setPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
    }

    fun chip(value: String, color: Int, fill: Int): TextView = text(value, 12, color, true).apply {
        background = context.rounded(fill, 20)
        setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
    }

    fun button(label: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isEnabled = enabled
            minHeight = context.dp(48)
            minimumHeight = context.dp(48)
            stateListAnimator = null
            elevation = 0f
            val fill = when {
                !enabled -> PulseColor.line
                primary -> PulseColor.blue
                else -> PulseColor.paleBlue
            }
            setTextColor(when {
                !enabled -> PulseColor.muted
                primary -> PulseColor.surface
                else -> PulseColor.blue
            })
            background = RippleDrawable(ColorStateList.valueOf(0x240967CD), context.rounded(fill, 15), null)
            setPadding(context.dp(14), 0, context.dp(14), 0)
            setOnClickListener { action() }
        }
}

internal enum class Glyph { PULSE, BAND, HEART, STEPS, HISTORY }

internal class PulseIcon(context: Context, private val glyph: Glyph, color: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.75f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        canvas.save()
        canvas.translate((width - size) / 2, (height - size) / 2)
        canvas.scale(size / 24, size / 24)
        path.reset()
        when (glyph) {
            Glyph.PULSE -> {
                path.moveTo(2f, 12f)
                path.lineTo(7f, 12f)
                path.lineTo(10f, 5f)
                path.lineTo(14f, 19f)
                path.lineTo(17f, 10f)
                path.lineTo(19f, 12f)
                path.lineTo(22f, 12f)
            }
            Glyph.BAND -> {
                canvas.drawRoundRect(6f, 6f, 18f, 18f, 4f, 4f, pen)
                path.moveTo(9f, 6f)
                path.lineTo(10f, 2f)
                path.lineTo(14f, 2f)
                path.lineTo(15f, 6f)
                path.moveTo(9f, 18f)
                path.lineTo(10f, 22f)
                path.lineTo(14f, 22f)
                path.lineTo(15f, 18f)
                path.moveTo(12f, 9f)
                path.lineTo(12f, 12f)
                path.lineTo(14f, 13f)
            }
            Glyph.HEART -> {
                path.moveTo(12f, 20f)
                path.cubicTo(9f, 18f, 3f, 13.5f, 3f, 8.5f)
                path.cubicTo(3f, 4f, 9f, 2.5f, 12f, 7f)
                path.cubicTo(15f, 2.5f, 21f, 4f, 21f, 8.5f)
                path.cubicTo(21f, 13.5f, 15f, 18f, 12f, 20f)
                path.close()
            }
            Glyph.STEPS -> {
                canvas.drawOval(3f, 5f, 9f, 12f, pen)
                canvas.drawOval(15f, 9f, 21f, 16f, pen)
                canvas.drawRoundRect(4f, 14f, 8f, 18f, 1.5f, 1.5f, pen)
                canvas.drawRoundRect(16f, 18f, 20f, 22f, 1.5f, 1.5f, pen)
            }
            Glyph.HISTORY -> {
                canvas.drawCircle(12f, 12f, 8.5f, pen)
                path.moveTo(12f, 7f)
                path.lineTo(12f, 12f)
                path.lineTo(15.5f, 14f)
            }
        }
        canvas.drawPath(path, pen)
        canvas.restore()
    }
}

internal class HeartChart(context: Context, private val samples: List<HeartSample>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val area = Path()

    init {
        val min = samples.minOf { it.bpm }
        val max = samples.maxOf { it.bpm }
        contentDescription = "心率趋势，最低 $min，最高 $max 次每分钟"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = context.dp(2).toFloat()
        val right = (width - context.dp(31)).toFloat()
        val top = context.dp(9).toFloat()
        val bottom = (height - context.dp(24)).toFloat()
        if (right <= left || bottom <= top) return

        val low = (samples.minOf { it.bpm } / 10) * 10
        val high = maxOf(low + 20, ((samples.maxOf { it.bpm } + 9) / 10) * 10)
        val first = samples.first().minuteOfDay
        val last = samples.last().minuteOfDay
        paint.textSize = context.dp(10).toFloat()
        paint.strokeWidth = context.dp(1).toFloat()
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        for (index in 0..2) {
            val y = top + (bottom - top) * index / 2
            paint.color = PulseColor.line
            canvas.drawLine(left, y, right, y, paint)
            paint.color = PulseColor.muted
            canvas.drawText((high - (high - low) * index / 2).toString(), right + context.dp(7), y + context.dp(3), paint)
        }

        line.reset()
        var endX = left
        var endY = top
        samples.forEachIndexed { index, sample ->
            val fraction = if (last > first) {
                (sample.minuteOfDay - first).toFloat() / (last - first)
            } else {
                index.toFloat() / samples.lastIndex.coerceAtLeast(1)
            }
            endX = left + (right - left) * fraction
            endY = bottom - (bottom - top) * (sample.bpm - low) / (high - low)
            if (index == 0) line.moveTo(endX, endY) else line.lineTo(endX, endY)
        }

        area.set(line)
        area.lineTo(endX, bottom)
        area.lineTo(left, bottom)
        area.close()
        paint.shader = LinearGradient(0f, top, 0f, bottom, 0x42096EDC, 0x00096EDC, Shader.TileMode.CLAMP)
        canvas.drawPath(area, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = context.dp(2).toFloat()
        paint.color = PulseColor.blue
        canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(endX, endY, context.dp(4).toFloat(), paint)

        paint.color = PulseColor.muted
        val labelY = height - context.dp(4).toFloat()
        canvas.drawText(timeLabel(first), left, labelY, paint)
        if (last != first) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeLabel(last), right, labelY, paint)
        }
    }

    private fun timeLabel(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
}
