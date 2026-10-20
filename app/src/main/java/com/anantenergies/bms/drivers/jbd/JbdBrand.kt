package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.ble.GattProfile
import com.anantenergies.bms.ble.uuid16
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsBrand
import com.anantenergies.bms.domain.BmsDriver
import kotlinx.coroutines.CoroutineScope

/** JBD (Xiaoxiang) boards, including the DB24SA08. */
class JbdBrand(
    private val scope: CoroutineScope,
    private val links: GattLinkFactory,
    private val log: TrafficLog?,
) : BmsBrand {
    override val id = ID
    override val name = "JBD"
    override val serviceUuid = SERVICE

    override fun matches(advertisement: Advertisement) = Companion.matches(advertisement)

    override fun create(): BmsDriver = JbdDriver(scope, links, log)

    companion object {
        const val ID = "jbd"

        /** Expected from the public protocol; to be confirmed on the real board. */
        val PROFILE = GattProfile(service = uuid16(0xFF00), notify = uuid16(0xFF01), write = uuid16(0xFF02))

        private val SERVICE = PROFILE.service.toString()

        /** Default names of JBD Bluetooth modules seen in the field; to be confirmed on the real board. */
        private val NAME = Regex("^(jbd|xiaoxiang|sp\\d{2}s|dp\\d{2}s|up\\d{2}s|db\\d{2}s)", RegexOption.IGNORE_CASE)

        fun matches(advertisement: Advertisement): Boolean =
            SERVICE in advertisement.serviceUuids || advertisement.name?.trim()?.let { NAME.containsMatchIn(it) } == true
    }
}
