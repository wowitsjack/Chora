package com.craftworks.music.managers.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceModeTest {
    @Test
    fun missingOrInvalidStorageFallsBackToChora() {
        assertEquals(InterfaceMode.CHORA, InterfaceMode.fromStorage(null))
        assertEquals(InterfaceMode.CHORA, InterfaceMode.fromStorage(""))
        assertEquals(InterfaceMode.CHORA, InterfaceMode.fromStorage("UNKNOWN"))
    }

    @Test
    fun storedIpodModeRoundTrips() {
        assertEquals(
            InterfaceMode.IPOD_TOUCH,
            InterfaceMode.fromStorage(InterfaceMode.IPOD_TOUCH.name)
        )
    }
}
