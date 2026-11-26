package com.example.chess_screen_assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {
    private val channelName = "chess_assistant/screen_capture"
    private var mediaProjection: MediaProjection? = null
    private var pendingPermissionResult: MethodChannel.Result? = null
    private var pendingCaptureResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestPermission" -> requestPermission(result)
                    "capture" -> captureScreen(result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun requestPermission(result: MethodChannel.Result) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        pendingPermissionResult = result
        startActivityForResult(mpm.createScreenCaptureIntent(), 1001)
    }

    private fun captureScreen(result: MethodChannel.Result) {
        val projection = mediaProjection
        if (projection == null) {
            result.error("NO_PERMISSION", "MediaProjection not initialized", null)
            return
        }
        pendingCaptureResult = result

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "chess_screen", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        val handler = Handler(Looper.getMainLooper())
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image == null) return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val bytes = baos.toByteArray()
                handler.post {
                    pendingCaptureResult?.success(bytes)
                    pendingCaptureResult = null
                }
            } catch (e: Exception) {
                handler.post {
                    pendingCaptureResult?.error("CAPTURE_FAILED", e.message, null)
                    pendingCaptureResult = null
                }
            } finally {
                image.close()
                imageReader.close()
                virtualDisplay.release()
            }
        }, handler)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mpm.getMediaProjection(resultCode, data)
                pendingPermissionResult?.success(true)
            } else {
                pendingPermissionResult?.success(false)
            }
            pendingPermissionResult = null
        }
    }
}
