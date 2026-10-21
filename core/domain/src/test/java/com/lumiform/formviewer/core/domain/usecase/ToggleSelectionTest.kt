package com.lumiform.formviewer.core.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToggleSelectionTest {
    @Test
    fun singleSelect_selectsTappedResponse() {
        assertEquals(setOf(1L), toggleSelection(emptySet(), 1, multipleSelection = false))
    }

    @Test
    fun singleSelect_replacesPreviousSelection() {
        assertEquals(setOf(2L), toggleSelection(setOf(1), 2, multipleSelection = false))
    }

    @Test
    fun singleSelect_retapClearsSelection() {
        assertEquals(emptySet<Long>(), toggleSelection(setOf(1), 1, multipleSelection = false))
    }

    @Test
    fun singleSelect_withInconsistentSelection_endsWithAtMostOne() {
        assertEquals(setOf(3L), toggleSelection(setOf(1, 2), 3, multipleSelection = false))
        assertEquals(emptySet<Long>(), toggleSelection(setOf(1, 2), 1, multipleSelection = false))
    }

    @Test
    fun multiSelect_addsTappedResponse() {
        assertEquals(setOf(1L, 2L), toggleSelection(setOf(1), 2, multipleSelection = true))
    }

    @Test
    fun multiSelect_retapRemovesOnlyThatResponse() {
        assertEquals(setOf(2L), toggleSelection(setOf(1, 2), 1, multipleSelection = true))
    }
}
