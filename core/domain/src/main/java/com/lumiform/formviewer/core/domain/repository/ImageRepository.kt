package com.lumiform.formviewer.core.domain.repository

interface ImageRepository {
    /** Downloads [urls] into the offline image cache; returns false if any of them failed. */
    suspend fun prefetch(urls: Set<String>): Boolean
}
