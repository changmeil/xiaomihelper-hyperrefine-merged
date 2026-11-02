package cn.ianzb.hyperrefine.hook.splash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import java.util.zip.ZipFile

/**
 * 内置替换图的读取与缓存。
 *
 * 图放在**模块 APK 自己的 assets** 里，目标进程（学习通 / SystemUI / system_server）拿不到我们的
 * `Context`，只能按 zip entry 直接从 APK 读 —— 这是上游模块的做法，也是唯一不依赖文件权限的路子。
 */
object SplashAssets {

    /** 模块 APK 路径，由入口在 `onModuleLoaded` 里塞进来。 */
    @Volatile
    private var apkPath: String? = null

    @Volatile
    private var bitmap: Bitmap? = null

    /** 留白底色，跟 bitmap 一起算一次。 */
    @Volatile
    private var marginColor: Int = Color.WHITE

    @Volatile
    private var tried = false

    /** 记录模块 APK 路径；路径变了就丢掉旧缓存（覆盖安装后路径不变，正常不会触发）。 */
    fun setModuleApk(path: String?) {
        if (path.isNullOrEmpty() || path == apkPath) return
        synchronized(this) {
            if (path == apkPath) return
            apkPath = path
            bitmap = null
            tried = false
        }
    }

    fun bitmap(): Bitmap? {
        bitmap?.let { return it }
        synchronized(this) {
            bitmap?.let { return it }
            if (tried) return null
            val path = apkPath
            if (path == null) {
                // 路径还没就绪（入口比 hook 晚），下次再试
                return null
            }
            tried = true
            val loaded = runCatching {
                ZipFile(path).use { zip ->
                    val entry = zip.getEntry(SplashKeys.ASSET) ?: return@use null
                    zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                }
            }.onFailure { Log.e(SplashKeys.TAG, "load ${SplashKeys.ASSET} failed", it) }
                .getOrNull()

            bitmap = loaded
            if (loaded != null) {
                marginColor = marginColorOf(loaded)
                Log.i(SplashKeys.TAG, "bitmap loaded: ${loaded.width}x${loaded.height} from $path")
            } else {
                Log.e(SplashKeys.TAG, "asset not found or undecodable: ${SplashKeys.ASSET}")
            }
            return loaded
        }
    }

    /** 每次都要新对象：同一个 Drawable 不能同时挂在多个 View 上。 */
    fun drawable(): Drawable? {
        val bmp = bitmap() ?: return null
        return SplashDrawable(bmp, marginColor)
    }
}

/**
 * 取样边缘主色作为留白底色，取样点彼此差别较大时退回白色。
 * 这样 16:9 的图铺到 20:9 的屏幕上，上下留白不会突兀。
 */
private fun marginColorOf(b: Bitmap): Int {
    val w = b.width
    val h = b.height
    if (w < 8 || h < 8) return Color.WHITE
    // 向内收一点，避开原图可能自带的 1~几像素细边框
    val inset = minOf(maxOf(2, minOf(w, h) / 40), minOf(w, h) / 4)
    val l = inset
    val r = w - 1 - inset
    val t = inset
    val bo = h - 1 - inset
    val samples = intArrayOf(
        b.getPixel(l, t), b.getPixel(r, t), b.getPixel(l, bo), b.getPixel(r, bo),
        b.getPixel(w / 2, t), b.getPixel(w / 2, bo),
        b.getPixel(l, h / 2), b.getPixel(r, h / 2),
    )
    var rr = 0
    var gg = 0
    var bb = 0
    for (c in samples) {
        rr += Color.red(c)
        gg += Color.green(c)
        bb += Color.blue(c)
    }
    rr /= samples.size
    gg /= samples.size
    bb /= samples.size
    for (c in samples) {
        if (kotlin.math.abs(Color.red(c) - rr) > 24 ||
            kotlin.math.abs(Color.green(c) - gg) > 24 ||
            kotlin.math.abs(Color.blue(c) - bb) > 24
        ) {
            return Color.WHITE
        }
    }
    return Color.rgb(rr, gg, bb)
}

/**
 * 等比缩放 + 居中（fit-center）：整图完整可见、不裁切、不变形，剩余空间填留白底色。
 */
internal class SplashDrawable(
    private val bitmap: Bitmap,
    private val marginColor: Int,
) : Drawable() {

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val marginPaint = Paint()
    private val matrix = Matrix()
    private var alphaValue = 255
    private var filterLogCount = 0

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        marginPaint.color = marginColor
        marginPaint.alpha = alphaValue
        canvas.drawRect(b, marginPaint)

        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) return

        val scale = minOf(b.width().toFloat() / bw, b.height().toFloat() / bh)
        val dx = b.left + (b.width() - bw * scale) / 2f
        val dy = b.top + (b.height() - bh * scale) / 2f

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)

        imagePaint.alpha = alphaValue
        canvas.drawBitmap(bitmap, matrix, imagePaint)
    }

    override fun setAlpha(alpha: Int) {
        if (alphaValue == alpha) return
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaValue

    /**
     * 系统（深色模式 / 强制深色）会给 windowBackground 的 drawable 设 colorFilter 把整屏压暗。
     * 这个 drawable 是「图片 + 留白」合成的一整份设计稿，filter 只作用到图片会让中间发灰、
     * 上下留白仍是雪白，观感割裂 —— 所以整体忽略，始终保持原图配色。
     */
    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (colorFilter != null && filterLogCount < 3) {
            filterLogCount++
            Log.i(SplashKeys.TAG, "ignored colorFilter (${colorFilter.javaClass.simpleName})")
        }
        // 不把 filter 应用到任何 paint：留白与图片必须同色系，否则深色模式下会一半灰一半白。
        // 这里没有状态变化，刻意不调用 invalidateSelf()，避免系统反复设 filter 时触发重绘循环。
    }

    override fun getOpacity(): Int = if (alphaValue == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height
}
