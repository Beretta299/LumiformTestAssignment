package com.lumiform.formviewer.core.data.remote

import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.data.remote.dto.UnsupportedItemDeserializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Headers

internal interface FormApi {
    /**
     * `max-age=0` revalidates OkHttp's cached copy on every call (a 304 when unchanged); `no-cache` would skip
     * the conditional request and always download the full body.
     *
     * Returns the raw [Response] because Retrofit fails a 2xx without a body with an unchecked exception.
     */
    @GET(FORM_PATH)
    @Headers("Cache-Control: max-age=0")
    suspend fun getForm(): Response<List<FormItemDto>>

    companion object {
        val BASE_URL: HttpUrl = "https://gist.githubusercontent.com/".toHttpUrl()
        const val FORM_PATH = "aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json"
    }
}

internal val FormJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Built-in sealed polymorphism fails on an unknown `type`; one new item type must not break the whole form.
        serializersModule =
            SerializersModule {
                polymorphicDefaultDeserializer(FormItemDto::class) { type -> UnsupportedItemDeserializer(type) }
            }
    }

internal fun createFormApi(
    baseUrl: HttpUrl,
    client: OkHttpClient,
    json: Json = FormJson,
): FormApi =
    Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()
