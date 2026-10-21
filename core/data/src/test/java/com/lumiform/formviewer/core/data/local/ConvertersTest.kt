package com.lumiform.formviewer.core.data.local

import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class ConvertersTest {
    private val converters = Converters()

    @ParameterizedTest
    @EnumSource(AnswerSyncState::class)
    fun answerSyncState_roundTripsThroughStoredValue(state: AnswerSyncState) {
        assertEquals(state, converters.toAnswerSyncState(converters.fromAnswerSyncState(state)))
    }

    @Test
    fun answerSyncState_storedValuesAreUnique() {
        val values = AnswerSyncState.entries.map { it.storedValue }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun answerSyncState_unknownStoredValue_fails() {
        assertThrows<IllegalStateException> { converters.toAnswerSyncState("archived") }
    }
}
