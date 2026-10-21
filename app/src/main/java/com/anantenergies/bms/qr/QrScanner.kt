package com.anantenergies.bms.qr

import android.content.Context
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/** What came of one tap on "Scan QR code". */
sealed interface QrScanResult {
    /** The code's text; empty if the code held no text. */
    data class Read(val text: String) : QrScanResult

    /** The user backed out of the scanner. */
    data object Cancelled : QrScanResult

    data class Unavailable(val message: String) : QrScanResult
}

/**
 * Reads one QR code with Google's code scanner. Google Play services shows the camera screen and
 * hands back the text, so the app itself needs no camera permission.
 */
object QrScanner {
    private val DOWNLOAD_TIMEOUT = 60.seconds

    /** [onDownloading] is called if Google Play services first has to download its scanner. */
    suspend fun scan(context: Context, onDownloading: () -> Unit): QrScanResult {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)

        val installed = try {
            ensureInstalled(context, scanner, onDownloading)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Typically a phone without Google Play services.
            return QrScanResult.Unavailable(
                "The QR scanner isn't available on this phone. Pick the BMS from the list below instead.",
            )
        }
        if (!installed) {
            return QrScanResult.Unavailable(
                "The QR scanner couldn't be downloaded. Google Play services needs the internet for this once; " +
                    "connect and try again, or pick the BMS from the list below.",
            )
        }

        return suspendCancellableCoroutine { cont ->
            scanner.startScan()
                .addOnSuccessListener { barcode -> cont.resumeOnce(QrScanResult.Read(barcode.rawValue.orEmpty())) }
                .addOnCanceledListener { cont.resumeOnce(QrScanResult.Cancelled) }
                .addOnFailureListener {
                    cont.resumeOnce(
                        QrScanResult.Unavailable("The QR scanner couldn't start. Try again, or pick the BMS from the list below."),
                    )
                }
        }
    }

    /** True once the scanner module is on the phone. False if downloading it failed or took too long. */
    private suspend fun ensureInstalled(context: Context, scanner: GmsBarcodeScanner, onDownloading: () -> Unit): Boolean {
        val modules = ModuleInstall.getClient(context)
        if (modules.areModulesAvailable(scanner).await().areModulesAvailable()) return true

        onDownloading()
        return withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                lateinit var listener: InstallStatusListener
                fun finish(ok: Boolean) {
                    modules.unregisterListener(listener)
                    cont.resumeOnce(ok)
                }
                listener = InstallStatusListener { update ->
                    when (update.installState) {
                        InstallState.STATE_COMPLETED -> finish(true)
                        InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> finish(false)
                    }
                }
                cont.invokeOnCancellation { modules.unregisterListener(listener) }
                modules.installModules(ModuleInstallRequest.newBuilder().addApi(scanner).setListener(listener).build())
                    .addOnSuccessListener { if (it.areModulesAlreadyInstalled()) finish(true) }
                    .addOnFailureListener { finish(false) }
            }
        } ?: false
    }

    private fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
        if (isActive) resume(value)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                task.isSuccessful -> cont.resume(task.result)
                else -> cont.resumeWithException(error ?: IllegalStateException("Google Play services task was cancelled"))
            }
        }
    }
}
