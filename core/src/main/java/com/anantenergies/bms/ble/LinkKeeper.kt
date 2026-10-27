package com.anantenergies.bms.ble

import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.domain.BmsConnectException
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps one board connected. [connect] makes the first connection (a few quick attempts); after
 * that a dropped link is reopened with backoff (1, 2, 4, 8, then every 15 s) until [close].
 * Brand-neutral: any driver can use it.
 */
class LinkKeeper(
    private val scope: CoroutineScope,
    private val open: (address: String) -> GattLink,
    private val log: TrafficLog?,
    /** Called with each newly opened link, before the state turns Connected. */
    private val onOpened: (GattLink) -> Unit,
    /** Called when the current link drops or is closed. */
    private val onLost: () -> Unit,
) {
    private val _state = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    @Volatile private var link: GattLink? = null
    @Volatile private var job: Job? = null

    /** Throws [BmsConnectException]. */
    suspend fun connect(address: String) {
        _state.value = LinkState.Connecting
        val first = try {
            openWithRetries(address)
        } catch (e: BmsConnectException) {
            _state.value = LinkState.Disconnected
            throw e
        }
        // Published before returning, so the caller can use the link straight away.
        publish(first)
        job = scope.launch { keepOpen(address, first) }
    }

    fun updateRssi(rssi: Int) {
        if (_state.value is LinkState.Connected) _state.value = LinkState.Connected(rssi)
    }

    fun close() {
        job?.cancel()
        job = null
        link?.close()
        link = null
        onLost()
        _state.value = LinkState.Disconnected
    }

    private suspend fun openWithRetries(address: String): GattLink {
        var lastError = "Couldn't connect"
        repeat(INITIAL_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(INITIAL_RETRY_DELAY_MS * attempt)
            val candidate = open(address)
            try {
                logServices(candidate.connect())
                return candidate
            } catch (e: GattServiceMissing) {
                candidate.close()
                logServices(e.services)
                log?.error("The expected service isn't on this board")
                throw BmsConnectException.NotSupported(e.services.describe())
            } catch (e: Exception) {
                candidate.close()
                if (e is CancellationException) throw e
                lastError = e.message ?: lastError
                log?.error("Connect attempt ${attempt + 1} failed: $lastError")
            }
        }
        throw BmsConnectException.Unreachable(lastError)
    }

    private fun publish(opened: GattLink) {
        link = opened
        onOpened(opened)
        _state.value = LinkState.Connected(rssi = null)
    }

    private suspend fun keepOpen(address: String, first: GattLink) {
        var current = first
        while (true) {
            current.dropped.first { it }
            log?.info("Link dropped")
            onLost()
            current.close()
            link = null
            current = reopen(address)
            publish(current)
        }
    }

    private suspend fun reopen(address: String): GattLink {
        var attempt = 0
        while (true) {
            attempt++
            _state.value = LinkState.Reconnecting(attempt)
            delay(backoffMs(attempt))
            val candidate = open(address)
            try {
                logServices(candidate.connect())
                return candidate
            } catch (e: Exception) {
                candidate.close()
                if (e is CancellationException) throw e
                log?.error("Reconnect attempt $attempt failed: ${e.message}")
            }
        }
    }

    private fun logServices(services: GattServices) {
        log?.info("Services found:\n" + services.describe().joinToString("\n"))
    }

    companion object {
        const val INITIAL_ATTEMPTS = 3
        const val INITIAL_RETRY_DELAY_MS = 1_000L

        fun backoffMs(attempt: Int): Long = when (attempt) {
            1 -> 1_000L
            2 -> 2_000L
            3 -> 4_000L
            4 -> 8_000L
            else -> 15_000L
        }
    }
}
