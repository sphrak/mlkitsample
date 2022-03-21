package com.example.androidxcameramlkitandfriends

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class CameraPreview constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    private val cameraPreview = PreviewView(context, attrs).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private var analysisUseCase: ImageAnalysis? = null
    private var previewUseCase: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val mutBarcodeFlow: MutableSharedFlow<List<QrCode>> = MutableSharedFlow(extraBufferCapacity = 1)
    val barcodeFlow: SharedFlow<List<QrCode>> = mutBarcodeFlow.asSharedFlow()

    data class QrCode(
        val barcode: Barcode,
        val height: Float,
        val width: Float,
    )

    private val screenAspectRatio: Int
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = DisplayMetrics().also {
                    cameraPreview.display?.getRealMetrics(it)
                }
                aspectRatio(metrics.widthPixels, metrics.heightPixels)
            } else {

                val metrics = DisplayMetrics().also {
                    cameraPreview.display?.getRealMetrics(it)
                }
                aspectRatio(metrics.widthPixels, metrics.heightPixels)
            }
        }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private val lensFacing = CameraSelector.LENS_FACING_BACK

    private val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    init {
        addView(cameraPreview)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val inputImage = InputImage.fromMediaImage(
            imageProxy.image!!,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                // todo
                println("hello barcodes: $barcodes")

                if (barcodes.isEmpty()) return@addOnSuccessListener

                val list = barcodes.map {
                    QrCode(
                        barcode = it,
                        height = imageProxy.height.toFloat(),
                        width = imageProxy.width.toFloat(),
                    )
                }

                 mutBarcodeFlow.tryEmit(list)
            }
            .addOnFailureListener {
                println("aaaaaa")
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun bindPreviewUseCase(): Preview {
        if (cameraProvider == null) {
            error("camera provider was null")
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(cameraPreview.display.rotation)
            .build()

        previewUseCase!!.setSurfaceProvider(cameraPreview.surfaceProvider)

        return previewUseCase!!
    }

    fun init() {

        check(cameraProvider == null)

        val provider = ProcessCameraProvider.getInstance(context)
        provider.addListener(
            {
                try {
                    cameraProvider = provider.get()

                    val preview = bindPreviewUseCase()
                    val analysis = bindImageAnalyserUseCase()

                    val useCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(analysis)
                        .setViewPort(cameraPreview.viewPort!!)
                        .build()

                    cameraProvider!!.unbindAll()

                    try {
                        cameraProvider!!.bindToLifecycle(
                            findViewTreeLifecycleOwner()!!,
                            cameraSelector,
                            useCaseGroup
                        )
                    } catch (illegalStateException: IllegalStateException) {
                        println(illegalStateException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        println(illegalArgumentException)
                    }

                    cameraPreview.visibility = View.VISIBLE
                    requestTransparentRegion(cameraPreview)
                } catch (e: ExecutionException) {
                    // Handle any errors (including cancellation) here.
                    println("Unhandled exception $e")
                } catch (e: InterruptedException) {
                    println("Unhandled exception $e")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun bindImageAnalyserUseCase(): ImageAnalysis {

        val options = BarcodeScannerOptions
            .Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

        if (cameraProvider == null) {
            error("cameraProvider was null")
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setOutputImageRotationEnabled(true)
            .setTargetRotation(cameraPreview.display.rotation)
            .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase!!.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)
        }

        return analysisUseCase!!
    }

}