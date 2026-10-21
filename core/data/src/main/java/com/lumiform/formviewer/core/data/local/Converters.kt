package com.lumiform.formviewer.core.data.local

import androidx.room.TypeConverter
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState

internal class Converters {
    @TypeConverter
    fun fromAnswerSyncState(state: AnswerSyncState): String = state.storedValue

    @TypeConverter
    fun toAnswerSyncState(value: String): AnswerSyncState = AnswerSyncState.fromStoredValue(value)
}
