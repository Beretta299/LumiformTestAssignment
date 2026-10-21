package com.lumiform.formviewer.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** Gives each test a fresh in-memory [FormDatabase] and closes it afterwards. */
internal class InMemoryDatabaseRule : ExternalResource() {
    lateinit var database: FormDatabase
        private set

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FormDatabase::class.java).build()
    }

    override fun after() {
        database.close()
    }
}
