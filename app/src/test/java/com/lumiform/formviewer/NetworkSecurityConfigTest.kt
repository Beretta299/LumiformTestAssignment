package com.lumiform.formviewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards the config against accidental weakening; the platform itself enforces the pins. */
class NetworkSecurityConfigTest {
    private val config: Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/network_security_config.xml"))
            .documentElement

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private val pinnedDomainConfigs: List<Element>
        get() = config.children("domain-config").filter { it.children("pin-set").isNotEmpty() }

    @Test
    fun cleartextTraffic_isDisabledByDefault() {
        val baseConfig = config.children("base-config").single()

        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))
    }

    @Test
    fun contentHosts_arePinned() {
        val pinnedDomains = pinnedDomainConfigs.flatMap { it.children("domain") }.map { it.textContent.trim() }

        assertTrue("gist.githubusercontent.com" in pinnedDomains, "pinned: $pinnedDomains")
        assertTrue("raw.githubusercontent.com" in pinnedDomains, "pinned: $pinnedDomains")
    }

    @Test
    fun everyPinSet_expires_andHasABackupPin() {
        val pinSets = config.children("pin-set")
        assertTrue(pinSets.isNotEmpty())

        pinSets.forEach { pinSet ->
            assertTrue(pinSet.getAttribute("expiration").isNotBlank(), "pin-set without expiration")
            val sha256Pins = pinSet.children("pin").filter { it.getAttribute("digest") == "SHA-256" }
            assertTrue(sha256Pins.size >= 2, "pin-set needs a backup pin, has ${sha256Pins.size}")
        }
    }
}
