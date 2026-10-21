package com.lumiform.formviewer.core.data.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.image.di.ImageModule
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Real image decoding needs Robolectric's native graphics; the default shadows can't decode.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
internal class CoilImageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private lateinit var imageLoader: ImageLoader
    private lateinit var repository: CoilImageRepository

    @Before
    fun setUp() {
        server.start()
        imageLoader = ImageModule.provideImageLoader(context, OkHttpClient())
        repository = CoilImageRepository(context, imageLoader)
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        server.close()
    }

    private fun url(path: String) = server.url(path).toString()

    @Test
    fun diskCache_livesInFilesDir() {
        assertThat(imageLoader.diskCache?.directory?.toFile()).isEqualTo(context.filesDir.resolve("image_cache"))
    }

    @Test
    fun prefetch_storesEveryImageInDiskCache() =
        runTest {
            repeat(2) { server.enqueue(MockResponse.Builder().body(Buffer().write(PNG)).build()) }

            assertThat(repository.prefetch(setOf(url("/a.png"), url("/b.png")))).isTrue()

            listOf("/a.png", "/b.png").forEach { path ->
                imageLoader.diskCache!!.openSnapshot(url(path)).use { assertThat(it).isNotNull() }
            }
        }

    @Test
    fun prefetch_reportsAFailedDownload_afterTryingTheRest() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).build())
            server.enqueue(MockResponse.Builder().body(Buffer().write(PNG)).build())

            assertThat(repository.prefetch(linkedSetOf(url("/gone.png"), url("/b.png")))).isFalse()
            assertThat(server.requestCount).isEqualTo(2)
        }

    private companion object {
        // A 1×1 transparent PNG.
        val PNG: ByteArray =
            java.util.Base64
                .getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
    }
}
