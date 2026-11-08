package app.pulse.wear

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private enum class Tab(val title: String, val icon: Glyph) {
        HEALTH("健康", Glyph.HEART), DEVICE("设备", Glyph.BAND), HISTORY("历史", Glyph.HISTORY),
    }

    private enum class Link { IDLE, SCANNING, FOUND, CONNECTING, PREPARING, READY }
    private enum class Sync { IDLE, WAITING, SAVED, TIMEOUT, SAVE_FAILED }

    private val ui by lazy { PulseUi(this) }
    private val client: WearableClient = MockWearableClient()
    private lateinit var history: HealthHistory
    private lateinit var records: List<HealthRecord>
    private lateinit var scroll: ScrollView
    private var tab = Tab.DEVICE
    private var link = Link.IDLE
    private var sync = Sync.IDLE
    private var device: Wearable? = null
    private var timeoutNext = false
    private var openRecord: HealthRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HealthHistory(this)
        records = history.load()
        val savedTab = savedInstanceState?.getString("tab")
        tab = Tab.entries.firstOrNull { it.name == savedTab } ?: Tab.DEVICE
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        client.disconnect()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            openRecord != null -> navigate(Tab.HISTORY)
            tab != Tab.DEVICE -> navigate(Tab.DEVICE)
            else -> super.onBackPressed()
        }
    }

    private fun navigate(destination: Tab) {
        tab = destination
        openRecord = null
        render()
    }

    private fun scan() {
        link = Link.SCANNING
        render()
        client.scan { found ->
            device = found
            link = Link.FOUND
            render(keepPosition = true)
        }
    }

    private fun connect(found: Wearable) {
        link = Link.CONNECTING
        render(keepPosition = true)
        client.connect(found, onPreparing = {
            link = Link.PREPARING
            render(keepPosition = true)
        }, onReady = { ready ->
            device = ready
            link = Link.READY
            render()
        })
    }

    private fun disconnect() {
        client.disconnect()
        device = null
        link = Link.IDLE
        sync = Sync.IDLE
        timeoutNext = false
        navigate(Tab.DEVICE)
    }

    private fun syncHealth() {
        if (link != Link.READY || sync == Sync.WAITING) return
        val dropReply = timeoutNext
        timeoutNext = false
        sync = Sync.WAITING
        render(keepPosition = true)
        client.requestHealth(dropReply) { reply ->
            sync = when (reply) {
                SyncReply.Timeout -> Sync.TIMEOUT
                is SyncReply.Data -> try {
                    records = history.save(reply.record, records)
                    Sync.SAVED
                } catch (_: IOException) {
                    Sync.SAVE_FAILED
                }
            }
            render(keepPosition = true)
        }
    }

    private fun render(keepPosition: Boolean = false) {
        val offset = if (keepPosition && ::scroll.isInitialized) scroll.scrollY else 0
        val root = ui.column().apply { setBackgroundColor(PulseColor.background) }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = ui.column().apply { setPadding(dp(22), dp(16), dp(22), dp(18)) }
        content.addView(brandBar())
        content.gap(18)
        when (tab) {
            Tab.HEALTH -> showHealth(content)
            Tab.DEVICE -> showDevice(content)
            Tab.HISTORY -> showHistory(content)
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(navigationBar())
        setContentView(root)
        if (offset > 0) {
            val currentScroll = scroll
            currentScroll.post { currentScroll.scrollTo(0, offset) }
        }
    }

    private fun brandBar(): LinearLayout = ui.row().apply {
        addView(iconTile(Glyph.PULSE, 32, PulseColor.surface, PulseColor.blue))
        addView(ui.text("Pulse Wear", 18, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            marginStart = dp(9)
        })
        addView(ui.chip("MOCK 演示", PulseColor.blue, PulseColor.paleBlue))
    }

    private fun navigationBar(): LinearLayout = ui.column().apply {
        divider()
        addView(ui.row().apply {
            setBackgroundColor(PulseColor.surface)
            setPadding(dp(14), dp(5), dp(14), dp(5))
            for (item in Tab.entries) {
                val selected = item == tab
                val color = if (selected) PulseColor.blue else PulseColor.muted
                val entry = ui.column().apply {
                    gravity = Gravity.CENTER
                    minimumHeight = dp(54)
                    isSelected = selected
                    isFocusable = true
                    contentDescription = item.title
                    if (selected) background = rounded(PulseColor.paleBlue, 15)
                    addView(PulseIcon(context, item.icon, color), LinearLayout.LayoutParams(dp(22), dp(22)))
                    addView(ui.text(item.title, 11, color, true).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    })
                    setOnClickListener { navigate(item) }
                }
                addView(entry, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                })
            }
        })
    }

    private fun showDevice(content: LinearLayout) = with(content) {
        heading("设备", "发现、连接并查看穿戴设备信息")
        gap(16)
        val selected = device
        if (link == Link.READY && selected != null) {
            connectedDevice(this, selected)
            return@with
        }

        val (title, detail) = when (link) {
            Link.SCANNING -> "正在扫描" to "正在发现附近的模拟 BLE 设备…"
            Link.FOUND -> "找到 1 台手环" to "选择下方的设备进行连接。"
            Link.CONNECTING -> "正在连接" to "正在与 Pulse Band S1 建立连接…"
            Link.PREPARING -> "准备数据通道" to "正在发现服务并订阅模拟回包…"
            else -> "发现附近设备" to "扫描模拟手环，开启健康数据同步。"
        }
        addView(ui.card(22).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(iconTile(Glyph.BAND, 68))
            gap(12)
            addView(ui.text(title, 20, bold = true).apply { gravity = Gravity.CENTER })
            gap(4)
            addView(ui.text(detail, 13, PulseColor.muted).apply { gravity = Gravity.CENTER })
        })
        gap(12)
        when (link) {
            Link.IDLE -> addView(ui.button("扫描设备", action = ::scan))
            Link.SCANNING -> addView(ui.button("正在扫描…", enabled = false) {})
            Link.FOUND -> if (selected != null) {
                addView(ui.card().apply {
                    deviceRow(selected, "可连接", PulseColor.blue, PulseColor.paleBlue)
                    gap(12)
                    addView(ui.button("连接手环") { connect(selected) })
                })
            }
            else -> addView(ui.button("请稍候…", enabled = false) {})
        }
        gap(12)
        addView(ui.text("模拟环境 · 可以直接扫描，无需开启系统蓝牙。", 12, PulseColor.muted))
    }

    private fun connectedDevice(content: LinearLayout, wearable: Wearable) = with(content) {
        val lastSync = records.firstOrNull { it.deviceId == wearable.id }
        addView(ui.card().apply {
            deviceRow(wearable, "已连接", PulseColor.green, PulseColor.paleGreen)
            gap(16)
            divider()
            detailRow("设备型号", wearable.model)
            divider()
            detailRow("固件版本", wearable.firmware)
            divider()
            detailRow("电池电量", "${wearable.batteryPercent}%")
            divider()
            detailRow("信号强度", "${wearable.signalDbm} dBm")
            divider()
            detailRow("上次同步", lastSync?.let { "${it.day} ${timeOf(it.syncedAt)}" } ?: "尚未同步")
        })
        gap(12)
        addView(ui.button("查看健康概览") { navigate(Tab.HEALTH) })
        gap(12)
        addView(ui.card(14).apply {
            addView(ui.text("演示控制", 12, PulseColor.muted, true))
            addView(Switch(this@MainActivity).apply {
                text = "模拟下次同步超时"
                textSize = 15f
                setTextColor(PulseColor.ink)
                minimumHeight = dp(48)
                isChecked = timeoutNext
                setOnCheckedChangeListener { _, checked -> timeoutNext = checked }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(ui.text("只影响下一次同步，随后可正常重试。", 12, PulseColor.muted))
        })
        gap(10)
        addView(ui.button("断开连接", primary = false, action = ::disconnect))
    }

    private fun LinearLayout.deviceRow(wearable: Wearable, status: String, color: Int, fill: Int) {
        addView(ui.row().apply {
            addView(iconTile(Glyph.BAND, 44))
            val labels = ui.column().apply {
                addView(ui.text(wearable.name, 17, bold = true))
                addView(ui.text("模拟 BLE 手环", 12, PulseColor.muted))
            }
            addView(labels, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
            addView(ui.chip(status, color, fill))
        })
    }

    private fun showHealth(content: LinearLayout) = with(content) {
        val today = LocalDate.now().toString()
        val currentDevice = device?.id
        val record = records.firstOrNull { it.day == today && (currentDevice == null || it.deviceId == currentDevice) }
        heading("健康概览", if (link == Link.READY) "${device?.name} · 已连接" else "连接模拟手环，查看健康数据")
        gap(14)
        addView(heartCard(record))
        gap(10)
        addView(stepsCard(record))
        gap(12)
        if (link != Link.READY) {
            addView(ui.button("前往设备页") { navigate(Tab.DEVICE) })
            return@with
        }

        val label = when (sync) {
            Sync.WAITING -> "正在同步…"
            Sync.TIMEOUT, Sync.SAVE_FAILED -> "重试同步"
            else -> "同步健康数据"
        }
        addView(ui.button(label, enabled = sync != Sync.WAITING, action = ::syncHealth))
        when (sync) {
            Sync.IDLE -> if (record != null) {
                gap(7)
                addView(ui.text("上次同步 ${timeOf(record.syncedAt)}", 12, PulseColor.muted))
            }
            Sync.WAITING -> status("正在等待手环回包", "收到数据后将自动写入本地历史。", PulseColor.blue, PulseColor.paleBlue)
            Sync.SAVED -> status("同步成功", "心率和步数已保存到本地历史。", PulseColor.green, PulseColor.paleGreen)
            Sync.TIMEOUT -> status("同步超时", "手环在规定时间内未回复。请点击重试，之前的数据仍可查看。", PulseColor.amber, PulseColor.paleAmber)
            Sync.SAVE_FAILED -> status("保存失败", "本次数据未保存。请点击重试。", PulseColor.amber, PulseColor.paleAmber)
        }
    }

    private fun heartCard(record: HealthRecord?): LinearLayout = ui.card().apply {
        addView(ui.row().apply {
            addView(iconTile(Glyph.HEART, 33))
            addView(ui.text("心率", 15, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            })
            addView(ui.chip(if (record == null) "待同步" else "${record.heartSamples.size} 个样本", PulseColor.blue, PulseColor.paleBlue))
        })
        addView(ui.row().apply {
            gravity = Gravity.BOTTOM
            addView(ui.text(record?.latestBpm?.toString() ?: "—", 60, bold = true).apply {
                includeFontPadding = false
                fontFeatureSettings = "tnum"
            })
            addView(ui.text("bpm", 18, PulseColor.muted, true), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                marginStart = dp(7)
                bottomMargin = dp(8)
            })
        })
        gap(7)
        addView(ui.text(if (record == null) "同步后显示最近读数和趋势" else "最近读数 · 心率趋势", 12, PulseColor.muted))
        if (record != null) {
            gap(5)
            addView(HeartChart(this@MainActivity, record.heartSamples), LinearLayout.LayoutParams(MATCH_PARENT, dp(99)))
        }
    }

    private fun stepsCard(record: HealthRecord?): LinearLayout = ui.card(14).apply {
        addView(ui.row().apply {
            addView(iconTile(Glyph.STEPS, 38))
            addView(ui.text("${if (record != null && record.day != LocalDate.now().toString()) "当日" else "今日"}步数", 15, bold = true),
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(9) })
            addView(ui.text(record?.steps?.let(::formatSteps) ?: "—", 24, bold = true))
            addView(ui.text(" 步", 13, PulseColor.muted))
        })
    }

    private fun showHistory(content: LinearLayout) = with(content) {
        val selected = openRecord
        if (selected != null) {
            heading("记录详情", "${selected.day} · ${selected.deviceName}")
            gap(14)
            addView(heartCard(selected))
            gap(10)
            addView(stepsCard(selected))
            gap(8)
            addView(ui.text("保存于 ${timeOf(selected.syncedAt)} · 模拟数据", 12, PulseColor.muted))
            gap(12)
            addView(ui.button("返回历史列表", primary = false) { navigate(Tab.HISTORY) })
            return@with
        }

        heading("健康历史", "按日期保存，每台设备每天保留一条记录")
        gap(16)
        if (records.isEmpty()) {
            addView(ui.card(22).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(iconTile(Glyph.HISTORY, 60))
                gap(10)
                addView(ui.text("暂无同步记录", 18, bold = true))
                addView(ui.text("完成一次同步后，记录会保存在这里。", 13, PulseColor.muted))
            })
            gap(12)
            addView(ui.button("前往健康概览", primary = false) { navigate(Tab.HEALTH) })
            return@with
        }

        records.forEach { record ->
            addView(ui.card().apply {
                addView(ui.row().apply {
                    addView(ui.text(record.day, 18, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(ui.chip("本地保存", PulseColor.green, PulseColor.paleGreen))
                })
                addView(ui.text("${record.deviceName} · 同步于 ${timeOf(record.syncedAt)}", 12, PulseColor.muted))
                gap(9)
                addView(ui.text("${record.latestBpm} bpm    ·    ${formatSteps(record.steps)} 步", 16, bold = true))
                gap(8)
                addView(ui.button("查看记录", primary = false) {
                    openRecord = record
                    render()
                })
            })
            gap(10)
        }
    }

    private fun iconTile(glyph: Glyph, size: Int, color: Int = PulseColor.blue, fill: Int = PulseColor.paleBlue): View {
        return FrameLayout(this).apply {
            val side = dp(size)
            layoutParams = LinearLayout.LayoutParams(side, side)
            background = rounded(fill, size / 3)
            val iconSize = dp(size * 5 / 9)
            addView(PulseIcon(context, glyph, color), FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        }
    }

    private fun LinearLayout.heading(title: String, subtitle: String) {
        addView(ui.text(title, 31, bold = true).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
        })
        addView(ui.text(subtitle, 14, PulseColor.muted))
    }

    private fun LinearLayout.detailRow(label: String, value: String) {
        addView(ui.row().apply {
            minimumHeight = dp(39)
            addView(ui.text(label, 13, PulseColor.muted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(ui.text(value, 14, bold = true))
        })
    }

    private fun LinearLayout.status(title: String, detail: String, color: Int, fill: Int) {
        gap(8)
        addView(ui.column().apply {
            background = rounded(fill, 15)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(ui.text(title, 14, color, true))
            addView(ui.text(detail, 12, color))
        })
    }

    private fun LinearLayout.gap(size: Int) {
        addView(View(context), LinearLayout.LayoutParams(1, dp(size)))
    }

    private fun LinearLayout.divider() {
        addView(View(context).apply { setBackgroundColor(PulseColor.line) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
    }

    private fun timeOf(instant: Instant): String = CLOCK.format(instant.atZone(ZoneId.systemDefault()))

    private fun formatSteps(steps: Int): String = String.format(Locale.US, "%,d", steps)

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}
