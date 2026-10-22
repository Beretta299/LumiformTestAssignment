package com.lumiform.formviewer.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.repository() =
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "settings.preferences_pb") },
        )

    @Test
    fun showItemIds_isOffByDefault() =
        runTest(UnconfinedTestDispatcher()) {
            repository().showItemIds.test { assertFalse(awaitItem()) }
        }

    @Test
    fun setShowItemIds_isEmittedAndRemembered() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = repository()
            repository.showItemIds.test {
                assertEquals(false, awaitItem())
                repository.setShowItemIds(true)
                assertEquals(true, awaitItem())
                repository.setShowItemIds(true)
                expectNoEvents()
            }
        }
}
