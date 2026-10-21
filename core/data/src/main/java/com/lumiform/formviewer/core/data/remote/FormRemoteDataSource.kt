package com.lumiform.formviewer.core.data.remote

import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.data.remote.security.isCertificateRejection
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject

internal interface FormRemoteDataSource {
    /** @throws FormFetchException when the form can't be fetched, after transient failures were retried. */
    suspend fun fetchForm(): List<FormItemDto>
}

internal class RetrofitFormRemoteDataSource
    @Inject
    constructor(
        private val api: FormApi,
        private val retryPolicy: RetryPolicy,
    ) : FormRemoteDataSource {
        override suspend fun fetchForm(): List<FormItemDto> =
            retryPolicy.retry(shouldRetry = { it is FormFetchException && it.isTransient }) {
                val response =
                    try {
                        api.getForm()
                    } catch (e: IOException) {
                        throw e.toFetchException()
                    } catch (e: SerializationException) {
                        throw FormFetchException.MalformedResponse(e)
                    }
                if (!response.isSuccessful) throw FormFetchException.Http(response.code())
                response.body() ?: throw FormFetchException.MalformedResponse()
            }

        private fun IOException.toFetchException(): FormFetchException =
            if (isCertificateRejection()) FormFetchException.Insecure(this) else FormFetchException.Connectivity(this)
    }
