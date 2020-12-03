package com.wsj.ijkplayer.custom

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/** 视频播放组件 */
class IJKVideoView : FrameLayout, IJKPlayerControllerCallback {

    private var url: String? = null
    private var mediaPlayer: IjkMediaPlayer? = null
    private var controller: IJKPlayerController? = null
    private var videoAspectRatio: Float = 16 / 9F
    private var surfaceView: SurfaceView? = null
    private var surfaceViewCreated = false
    private val surfaceHolderCallback: SurfaceHolder.Callback by lazy { createSurfaceHolderCallback() }
    private var callback: IJKVideoViewCallback? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
            : super(context, attrs, defStyleAttr, defStyleRes) {
        setBackgroundColor(Color.BLACK)
        surfaceView = SurfaceView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            holder.addCallback(surfaceHolderCallback)
        }
        addView(surfaceView)
        controller = IJKPlayerController(context).apply {
            setIJKPlayerControllerCallback(this@IJKVideoView)
            setLoading(true)
        }
        addView(controller)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig == null) return
        viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    resizeVideo()
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )
    }

    /** 设置视频地址，加载完成后自动开始播放 */
    fun setUrl(url: String, title: String = "") {
        this.url = url
        controller?.setTitle(title)
        loadVideo()
    }

    /** 释放视频播放资源 */
    fun release() {
        controller?.release()
        surfaceView?.holder?.removeCallback(surfaceHolderCallback)
        surfaceView = null
        mediaPlayer?.run {
            stop()
            setDisplay(null)
            release()
        }
        mediaPlayer = null
    }

    /** 视频播放组件里点击监听 */
    fun setIJKVideoViewCallback(callback: IJKVideoViewCallback) {
        this.callback = callback
    }

    // 创建SurfaceHolder.Callback
    private fun createSurfaceHolderCallback() = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceViewCreated = true
            loadVideo()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceViewCreated = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        }
    }

    // 设置视频显示的宽高
    private fun resizeVideo() {
        surfaceView?.run {
            val frameWidth = this@IJKVideoView.width
            val frameHeight = this@IJKVideoView.height
            val frameRatio = 1F * frameWidth / frameHeight
            layoutParams = when {
                frameRatio == videoAspectRatio -> LayoutParams(frameWidth, frameHeight)
                frameRatio > videoAspectRatio -> {
                    val videoWidth = (frameHeight * videoAspectRatio).toInt()
                    LayoutParams(videoWidth, frameHeight).apply {
                        leftMargin = (frameWidth - videoWidth) / 2
                        topMargin = 0
                    }
                }
                else -> {
                    val videoHeight = (frameWidth / videoAspectRatio).toInt()
                    LayoutParams(frameWidth, videoHeight).apply {
                        topMargin = (frameHeight - videoHeight) / 2
                        leftMargin = 0
                    }
                }
            }
            requestLayout()
        }
    }

    // 加载视频
    private fun loadVideo() {
        if (!surfaceViewCreated || url?.isNotEmpty() != true) return
        createPlayer()
        try {
            mediaPlayer?.run {
                dataSource = url
                setDisplay(surfaceView?.holder)
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 创建播放器
    private fun createPlayer() {
        mediaPlayer?.run {
            stop()
            setDisplay(null)
            release()
        }
        mediaPlayer = IjkMediaPlayer().apply {
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
            // 强制使用tcp
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 60)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fps", 30)
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
            setOption(
                IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "overlay-format",
                IjkMediaPlayer.SDL_FCC_YV12.toLong()
            )
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "max-buffer-size", 1024)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 10)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", "4096")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", "2000000")
            setOnPreparedListener {
                Log.e("****IJKVideoView****", "setOnPreparedListener")
                controller?.run {
                    setVideoDuration(it.duration.div(1000).toInt())
                    setLoading(false)
                    setPlayingState(true, hide = false)
                }
            }
            setOnInfoListener { _, what, extra ->
                Log.e("****IJKVideoView****", "setOnInfoListener----what:$what----extra:$extra")
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) controller?.setLoading(true)
                else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) controller?.setLoading(false)
                false
            }
            setOnSeekCompleteListener {
                Log.e("****IJKVideoView****", "setOnSeekCompleteListener")
            }
            setOnBufferingUpdateListener { _, i ->
                Log.e("****IJKVideoView****", "setOnInfoListener----i:$i")
            }
            setOnErrorListener { _, what, extra ->
                Log.e("****IJKVideoView****", "setOnErrorListener----what:$what----extra:$extra")
                false
            }
            setOnVideoSizeChangedListener { _, width, height, _, _ ->
                videoAspectRatio = 1F * width / height
                resizeVideo()
            }
            setOnTimedTextListener { _, text ->
                Log.e("****IJKVideoView****", "setOnTimedTextListener----text:$text")
            }
        }
    }

    override fun back() {
        callback?.onBackClick()
    }

    override fun start() {
        mediaPlayer?.run { if (!isPlaying) start() }
    }

    override fun pause() {
        mediaPlayer?.run { if (isPlaying) pause() }
    }

    override fun seek(seconds: Int) {
        mediaPlayer?.seekTo(seconds * 1000L)
    }

    override fun fullScreen(fullScreen: Boolean) {
        callback?.onFullScreenClick(fullScreen)
    }

    override fun getProgress(): Int {
        return mediaPlayer?.currentPosition?.div(1000)?.toInt() ?: -1
    }
}

interface IJKVideoViewCallback {
    fun onBackClick()
    fun onFullScreenClick(fullScreen: Boolean)
}