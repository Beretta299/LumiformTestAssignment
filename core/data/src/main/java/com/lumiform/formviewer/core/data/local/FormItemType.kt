package com.lumiform.formviewer.core.data.local

/** Values of `form_item.type`; they match the payload's `type`, and any other value is an unsupported item. */
internal object FormItemType {
    const val PAGE = "page"
    const val SECTION = "section"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val CHOICE = "choice"
}
