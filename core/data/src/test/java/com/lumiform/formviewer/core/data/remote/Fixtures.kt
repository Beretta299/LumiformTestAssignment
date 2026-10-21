package com.lumiform.formviewer.core.data.remote

/** The payload served by the real endpoint, copied verbatim. */
internal fun formFixture(): String =
    checkNotNull(object {}.javaClass.getResource("/form.json")) { "form.json fixture missing" }.readText()
