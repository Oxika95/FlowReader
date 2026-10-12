package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlayRouteTest {
    private val car = RouteOutput(isBluetooth = true, address = "AA:BB:CC:DD:EE:FF")
    private val buds = RouteOutput(isBluetooth = true, address = "11:22:33:44:55:66")
    private val speaker = RouteOutput(isBluetooth = false, address = "")

    @Test
    fun matchesRoutedTarget() {
        assertTrue(UnderlayRoute.routedToTarget(listOf(car), "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun otherBluetoothDeviceDoesNotMatch() {
        assertFalse(UnderlayRoute.routedToTarget(listOf(buds), "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun speakerOnlyDoesNotMatch() {
        assertFalse(UnderlayRoute.routedToTarget(listOf(speaker), "AA:BB:CC:DD:EE:FF"))
        assertFalse(UnderlayRoute.routedToTarget(emptyList(), "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun blankTargetNeverMatches() {
        assertFalse(UnderlayRoute.routedToTarget(listOf(car), ""))
        assertFalse(UnderlayRoute.routedToTarget(listOf(RouteOutput(true, "")), "  "))
    }

    @Test
    fun nonBluetoothWithSameAddressDoesNotMatch() {
        assertFalse(UnderlayRoute.routedToTarget(listOf(RouteOutput(false, car.address)), car.address))
    }

    @Test
    fun ignoresCaseAndWhitespace() {
        assertTrue(UnderlayRoute.routedToTarget(listOf(RouteOutput(true, " aa:bb:cc:dd:ee:ff ")), "AA:BB:CC:DD:EE:FF"))
        assertTrue(UnderlayRoute.routedToTarget(listOf(car), " aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun describeListsOutputs() {
        assertEquals("none", UnderlayRoute.describe(emptyList()))
        assertEquals("bt:AA:BB:CC:DD:EE:FF,other", UnderlayRoute.describe(listOf(car, speaker)))
    }
}
