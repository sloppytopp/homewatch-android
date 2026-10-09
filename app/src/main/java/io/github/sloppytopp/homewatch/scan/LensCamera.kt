package io.github.sloppytopp.homewatch.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import io.github.sloppytopp.homewatch.detect.LensResult
import io.github.sloppytopp.homewatch.detect.LensSpotter

/**
 * Rear camera with the torch on and exposure turned down, so a lens glint stands out against a dark room.
 * Frames are analysed in memory and thrown away: nothing is stored, recorded or sent anywhere.
 */
class LensCamera(private val ctx: Context, private val onResult: (LensResult) -> Unit, private val onError: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var lastAt = 0L
    @Volatile private var closed = false

    /** Preview size chosen for the camera (landscape sensor orientation), and the sensor rotation, for the UI to lay out. */
    var previewW = 640; private set
    var previewH = 480; private set
    var sensorRotation = 90; private set

    @SuppressLint("MissingPermission")
    fun start(texture: android.graphics.SurfaceTexture) {
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
        if (id == null) { onError("This phone has no rear camera."); return }
        val ch = mgr.getCameraCharacteristics(id)
        sensorRotation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
        val size = sizes.filter { it.width <= 1280 && it.height <= 960 && it.width * 3 == it.height * 4 }.maxByOrNull { it.width * it.height }
            ?: sizes.filter { it.width <= 1280 }.maxByOrNull { it.width * it.height } ?: run { onError("No usable camera size."); return }
        previewW = size.width; previewH = size.height
        texture.setDefaultBufferSize(size.width, size.height)
        val previewSurface = Surface(texture)
        val t = HandlerThread("lens").also { it.start() }; thread = t
        val h = Handler(t.looper)
        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2); reader = r
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastAt < 120 || closed) return@setOnImageAvailableListener   // ~8 analyses a second is plenty
                lastAt = now
                val p = img.planes[0]   // Y plane = brightness
                val buf = p.buffer; val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                val res = LensSpotter.find(bytes, img.width, img.height, p.rowStride)
                main.post { if (!closed) onResult(res) }
            } finally { img.close() }
        }, h)
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    try {
                        cam.createCaptureSession(listOf(previewSurface, r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                try {
                                    val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(previewSurface); addTarget(r.surface)
                                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                        // expose for the highlights, not the shadows: a dark room stays dark, a glint stays bright
                                        ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it.lower) }
                                    }.build()
                                    s.setRepeatingRequest(req, null, h)
                                } catch (e: Exception) { main.post { onError("Couldn't start the camera: ${e.message}") } }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { main.post { onError("Couldn't configure the camera.") } }
                        }, h)
                    } catch (e: Exception) { main.post { onError("Couldn't start the camera: ${e.message}") } }
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close() }
                override fun onError(cam: CameraDevice, error: Int) { cam.close(); main.post { onError("The camera is busy or unavailable (error $error).") } }
            }, h)
        } catch (e: Exception) { onError("Couldn't open the camera: ${e.message}") }
    }

    /** Turns the torch off and releases everything. Safe to call twice. */
    fun stop() {
        closed = true
        runCatching { session?.stopRepeating() }; runCatching { session?.close() }; runCatching { device?.close() }; runCatching { reader?.close() }
        thread?.quitSafely(); session = null; device = null; reader = null; thread = null
    }
}
