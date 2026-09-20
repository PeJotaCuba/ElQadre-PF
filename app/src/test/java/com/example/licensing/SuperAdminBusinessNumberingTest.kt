package com.example.licensing

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperAdminBusinessNumberingTest {

    @Test
    fun testEmptyBusinessListReturns001() {
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(emptyList())
        assertEquals("001", next)
    }

    @Test
    fun testSequentialBusinesses() {
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(listOf("001", "002"))
        assertEquals("003", next)
    }

    @Test
    fun testDeletedFirstBusinessReuses001() {
        // If 001 and 002 exist and 001 is deleted -> next = 001
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(listOf("002"))
        assertEquals("001", next)
    }

    @Test
    fun testDeletedMiddleBusinessReuses002() {
        // If 001, 002 and 003 exist and 002 is deleted -> next = 002
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(listOf("001", "003"))
        assertEquals("002", next)
    }

    @Test
    fun testDeletedLastBusinessReturns003() {
        // If 001, 002 and 003 exist and 003 is deleted -> next = 003
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(listOf("001", "002"))
        assertEquals("003", next)
    }

    @Test
    fun testGapWithHigherNumbers() {
        // If 001, 003, 005 exist -> next = 002
        val next = SuperAdminBusinessManager.calculateNextAvailableBusinessCode(listOf("001", "003", "005"))
        assertEquals("002", next)
    }
}
