package com.wsj.ijkplayer.custom

import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.setPadding
import tv.danmaku.ijk.R
import java.util.*

/** 视频播放的控制器 */
class IJKPlayerController : LinearLayout {

    private var callback: IJKPlayerControllerCallback? = null // 事件回调
    private var curShowing = true // 当前控制器是否显示中
    private var stateAnimating = false // 当前控制器是否正在显示或者正在隐藏
    private var topView: View? = null // 顶部的返回按钮和标题等信息
    private var bottomView: View? = null // 底部的控制按钮和进度等信息
    private var centerView: View? = null // 中间的播放按钮，不在播放状态的时候显示
    private var loadingView: ProgressBar? = null // 中间的加载动画
    private var titleTv: TextView? = null // 标题
    private var playBtn: ImageView? = null // 播放暂停按钮
    private var playing: Boolean = false // 是否播放中
    private var fullScreenBtn: ImageView? = null // 全屏按钮
    private var fullScreen = false // 是否全屏中
    private var totalSeconds = 0 // 视频总时长
    private var durationTv: TextView? = null // 视频总时长显示
    private var progressTv: TextView? = null // 视频已播放时长显示
    private var seekBar: SeekBar? = null // 进度条
    private var timer: Timer? = null
    private var secondsToHide = 3 // 自动隐藏控制器的剩余时间

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes) {
        orientation = VERTICAL
        val density = context.resources.displayMetrics.density
        val barHeight = (50 * density).toInt()
        val barImgPadding = (15 * density).toInt()
        initTop(barHeight, barImgPadding)
        initCenter(density)
        initBottom(barHeight, barImgPadding)
        startTimer()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig == null) return
        fullScreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        fullScreenBtn?.setImageResource(if (fullScreen) R.drawable.ic_full_screen_exit else R.drawable.ic_full_screen)
    }

    /** 释放资源，主要是停止计时器 */
    fun release() {
        timer?.cancel()
        timer = null
    }

    /** 设置事件回调 */
    fun setIJKPlayerControllerCallback(callback: IJKPlayerControllerCallback) {
        this.callback = callback
    }

    /** 设置标题 */
    fun setTitle(title: String) {
        this.titleTv?.text = title
    }

    /** 设置进度条总时长 */
    fun setVideoDuration(seconds: Int) {
        totalSeconds = seconds
        if (seconds <= 0) {
            durationTv?.visibility = GONE
            seekBar?.visibility = GONE
        } else {
            durationTv?.text = seconds.formatSeconds()
            seekBar?.max = seconds
        }
    }

    /** 是否播放中 */
    fun setPlayingState(playing: Boolean, hide: Boolean = true) {
        this.playing = playing
        centerView?.visibility = if (playing) GONE else VISIBLE
        playBtn?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (playing && hide) hide() else show()
    }

    /** 是否加载中 */
    fun setLoading(loading: Boolean) {
        if (loading) {
            loadingView?.visibility = VISIBLE
            centerView?.visibility = GONE
        } else {
            loadingView?.visibility = GONE
            centerView?.visibility = if (playing) GONE else VISIBLE
        }
    }

    // 初始化顶部的返回按钮和标题等信息
    private fun initTop(barHeight: Int, barImgPadding: Int) {
        topView = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, barHeight)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#25101010"))
            // 返回按钮
            val backBtn = ImageView(context).apply {
                layoutParams = LayoutParams(barHeight, barHeight)
                setClickRipple()
                setPadding(barImgPadding)
                setImageResource(R.drawable.ic_back_white)
                setOnClickListener { callback?.back() }
            }
            addView(backBtn)
            // 标题，滚动暂时不做了
            titleTv = TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setTextColor(Color.WHITE)
                setLines(1)
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(titleTv)
        }
        addView(topView)
    }

    // 初始化中间的播放按钮，不在播放状态的时候显示，loading
    private fun initCenter(density: Float) {
        val centerSize = (60 * density).toInt()
        val loadingSize = (30 * density).toInt()
        centerView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(centerSize, centerSize)
                .apply { gravity = Gravity.CENTER }
            visibility = GONE
            setImageResource(R.drawable.ic_play)
            setClickRipple()
            setOnClickListener { onStartClick() }
        }
        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(loadingSize, loadingSize)
                .apply { gravity = Gravity.CENTER }
            visibility = GONE
        }
        val centerParent = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0).apply { weight = 1F }
            setOnClickListener { if (curShowing) hide() else show() }
            addView(centerView)
            addView(loadingView)
        }
        addView(centerParent)
    }

    // 初始化底部的控制按钮和进度等信息
    private fun initBottom(barHeight: Int, barImgPadding: Int) {
        bottomView = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, barHeight)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#25101010"))
            // 播放暂停按钮
            playBtn = ImageView(context).apply {
                layoutParams = LayoutParams(barHeight, barHeight)
                setClickRipple()
                setPadding(barImgPadding)
                setImageResource(R.drawable.ic_play)
                setOnClickListener {
                    if (playing) {
                        setPlayingState(false)
                        callback?.pause()
                    } else {
                        setPlayingState(true)
                        callback?.start()
                    }
                }
            }
            addView(playBtn)
            // 已播放时间
            progressTv = TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                setTextColor(Color.WHITE)
                text = "--:--"
            }
            addView(progressTv)
            // 进度条
            seekBar = SeekBar(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}

                    override fun onProgressChanged(sb: SeekBar, progress: Int, fu: Boolean) {
                        progressTv?.text = progress.formatSeconds()
                        secondsToHide = 99999999
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback?.seek(seekBar.progress)
                        secondsToHide = 3
                    }
                })
            }
            val seekBarParent = FrameLayout(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply { weight = 1F }
                addView(seekBar)
            }
            addView(seekBarParent)
            // 总时间
            durationTv = TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                setTextColor(Color.WHITE)
                text = "--:--"
            }
            addView(durationTv)
            // 全屏按钮
            fullScreenBtn = ImageView(context).apply {
                layoutParams = LayoutParams(barHeight, barHeight)
                setClickRipple()
                setPadding(barImgPadding)
                setImageResource(R.drawable.ic_full_screen)
                setOnClickListener { callback?.fullScreen(!fullScreen) }
            }
            addView(fullScreenBtn)
        }
        addView(bottomView)
    }

    // 开始隐藏控制器的计时
    private fun startTimer() {
        timer?.cancel()
        timer = null
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    // 自动隐藏控制器
                    secondsToHide--
                    if (secondsToHide == 0) post { hide() }
                    // 获取播放进度
                    if (playing) {
                        val progress = callback?.getProgress() ?: return
                        post {
                            progressTv?.text = progress.formatSeconds()
                            seekBar?.progress = progress.coerceAtLeast(0)
                        }
                    }
                }
            }, 1000, 1000)
        }
    }

    // 点播放按钮
    private fun onStartClick() {
        setPlayingState(true)
        callback?.start()
    }

    // 显示控制器
    private fun show() {
        secondsToHide = 3
        if (curShowing || stateAnimating) return
        curShowing = true
        topView?.run {
            if (translationY == 0F) return@run
            stateAnimating = true
            animate()
                .translationY(0F)
                .setDuration(250)
                .withEndAction { stateAnimating = false }
                .start()
        }
        bottomView?.run {
            if (translationY == 0F) return@run
            animate().translationY(0F).setDuration(250).start()
        }
    }

    // 隐藏控制器
    private fun hide() {
        if (!curShowing || stateAnimating) return
        curShowing = false
        topView?.run {
            if (translationY != 0F) return@run
            stateAnimating = true
            animate()
                .translationY(-height.toFloat())
                .setDuration(250)
                .withEndAction { stateAnimating = false }
                .start()
        }
        bottomView?.run {
            if (translationY != 0F) return@run
            animate().translationY(height.toFloat()).setDuration(250).start()
        }
    }
}

// 设置点击波纹
fun View.setClickRipple() {
    // 点击波纹效果
    val typedValue = TypedValue().apply {
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
    }
    val typedArray: TypedArray = context.theme.obtainStyledAttributes(
        typedValue.resourceId,
        intArrayOf(android.R.attr.selectableItemBackground)
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        foreground = typedArray.getDrawable(0)
    } else {
        background = typedArray.getDrawable(0)
    }
}

// 秒数转时分秒
fun Int.formatSeconds() = when {
    this < 0 -> "--:--"
    this < 60 -> "00:${this.toStringWithMin2Length()}"
    this < 3600 -> "${(this / 60).toStringWithMin2Length()}:${(this % 60).toStringWithMin2Length()}"
    else -> {
        val hour = this / 3600
        val tmp = this % 3600
        val minute = tmp / 60
        val second = tmp % 60
        "${hour}:${minute.toStringWithMin2Length()}:${second.toStringWithMin2Length()}"
    }
}

fun Int.toStringWithMin2Length() = if (this > 10) this.toString() else "0$this"

/** 视频播放的控制器的事件回调 */
interface IJKPlayerControllerCallback {
    fun back()
    fun start()
    fun pause()
    fun seek(seconds: Int)
    fun fullScreen(fullScreen: Boolean)
    fun getProgress(): Int
}