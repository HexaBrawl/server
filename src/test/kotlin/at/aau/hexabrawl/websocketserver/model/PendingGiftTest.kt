package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PendingGiftTest {

    @Test
    fun `pending gift exposes constructor arguments`() {
        val gift = PendingGift(ownerName = "Alice", delta = 7, pendingDecisions = 3)
        assertEquals("Alice", gift.ownerName)
        assertEquals(7, gift.delta)
        assertEquals(3, gift.pendingDecisions)
    }

    @Test
    fun `two pending gifts with same content are equal`() {
        val a = PendingGift("Alice", 5, 2)
        val b = PendingGift("Alice", 5, 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
