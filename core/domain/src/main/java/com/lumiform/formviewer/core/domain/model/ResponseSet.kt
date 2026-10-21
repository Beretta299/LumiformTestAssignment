package com.lumiform.formviewer.core.domain.model

data class ResponseSet(
    val id: Long,
    val multipleSelection: Boolean,
    val responses: List<Response>,
)

data class Response(
    val id: Long,
    val label: String,
    val score: Score,
)

/** The points a response adds to the form's result; a sealed type so scoring can gain rules later. */
sealed interface Score {
    data class Points(
        val value: Int,
    ) : Score

    data object NotScored : Score
}
