package com.lumiform.formviewer.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lumiform.formviewer.core.data.local.dao.AnswerDao
import com.lumiform.formviewer.core.data.local.dao.FormDao
import com.lumiform.formviewer.core.data.local.dao.SyncMetadataDao
import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity
import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        FormItemEntity::class,
        ResponseSetEntity::class,
        ResponseEntity::class,
        AnswerEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FormDatabase : RoomDatabase() {
    abstract fun formDao(): FormDao

    abstract fun answerDao(): AnswerDao

    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val NAME = "form.db"
    }
}
