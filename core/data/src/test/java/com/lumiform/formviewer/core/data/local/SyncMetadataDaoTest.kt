package com.lumiform.formviewer.core.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SyncMetadataDaoTest {
    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val dao get() = databaseRule.database.syncMetadataDao()

    @Test
    fun upsert_overwritesEntryWithSameKey() =
        runTest {
            dao.observe("form").test {
                assertThat(awaitItem()).isNull()

                dao.upsert(SyncMetadataEntity("form", lastSyncedAt = 1, lastError = null))
                assertThat(awaitItem()?.lastSyncedAt).isEqualTo(1L)

                dao.upsert(SyncMetadataEntity("form", lastSyncedAt = 1, lastError = "timeout"))
                assertThat(awaitItem()?.lastError).isEqualTo("timeout")
            }
        }

    @Test
    fun get_returnsStoredEntryOrNull() =
        runTest {
            assertThat(dao.get("form")).isNull()

            dao.upsert(SyncMetadataEntity("form", lastSyncedAt = 1, lastError = null))

            assertThat(dao.get("form")?.lastSyncedAt).isEqualTo(1L)
        }
}
