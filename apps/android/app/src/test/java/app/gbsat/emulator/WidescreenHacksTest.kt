package app.gbsat.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidescreenHacksTest {
    @Test
    fun panzerDragoonSagaMatchesItsRegionsAndParsesAllWrites() {
        val us = find("Panzer Dragoon Saga", region = "U")
        val pal = find("Azel: Panzer Dragoon Saga", region = "E")

        assertNotNull(us)
        assertNotNull(pal)
        assertEquals("Panzer Dragoon Saga", us!!.first)
        assertTrue(CheatParser.usable(us.second))
        assertEquals(3, CheatParser.parse(us.second)?.size)
        assertEquals(us.second, pal!!.second)
        assertNull(find("Panzer Dragoon Saga", region = "J"))
    }

    @Test
    fun skyTargetAndTombRaiderAreLimitedToDocumentedRegions() {
        val skyTarget = find("Sky Target", region = "U")
        val tombRaider = find("Tomb Raider", region = "E")

        assertEquals("16048D5A 0C00", skyTarget?.second)
        assertNull(find("Sky Target", region = "J"))
        assertEquals("16065288 0005", tombRaider?.second)
        assertTrue(CheatParser.usable(skyTarget!!.second))
        assertTrue(CheatParser.usable(tombRaider!!.second))
    }

    @Test
    fun virtuaFighterRequiresExactJapaneseProductAndRevision() {
        val supported = find("Virtua Fighter", product = "GS-9001", version = "V1.000", region = "J")

        assertEquals("Virtua Fighter (GS-9001 V1.000)", supported?.first)
        assertEquals("1602377A 00D2", supported?.second)
        assertNull(find("Virtua Fighter", product = "MK-81005", version = "V1.000", region = "U"))
        assertNull(find("Virtua Fighter", product = "GS-9001", version = "V1.004", region = "J"))
        assertNull(CheatParser.parse("80023779 D200"))
    }

    private fun find(
        title: String,
        region: String,
        product: String = "",
        version: String = "V1.000",
    ): Pair<String, String>? = WidescreenHacks.find(header(product, version, region, title), "$title.cue")

    private fun header(product: String, version: String, region: String, title: String): String {
        val chars = CharArray(256) { ' ' }
        fun write(offset: Int, size: Int, value: String) {
            value.take(size).forEachIndexed { index, char -> chars[offset + index] = char }
        }
        write(0x20, 10, product)
        write(0x2A, 6, version)
        write(0x40, 10, region)
        write(0x60, 112, title)
        return String(chars)
    }
}
