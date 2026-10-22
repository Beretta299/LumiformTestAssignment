package com.lumiform.formviewer.ui.form

import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormRowsTest {
    private fun text(id: Long) = TextQuestion(id, "Question $id")

    private fun choice(
        id: Long,
        selected: Set<Long>,
    ) = ChoiceQuestion(
        id = id,
        content = "Choice $id",
        responseSet =
            ResponseSet(
                id = id * 10,
                multipleSelection = true,
                responses =
                    listOf(
                        Response(id * 10 + 1, "Yes", Score.Points(2)),
                        Response(id * 10 + 2, "N/A", Score.NotScored),
                    ),
            ),
        selectedResponseIds = selected,
    )

    @Test
    fun toRows_flattensDepthFirst_numbersItemsPerPage_andGroupsEachPageChild() {
        val form =
            listOf(
                Page(
                    id = 1,
                    title = "Page",
                    children =
                        listOf(
                            text(2),
                            Section(
                                id = 3,
                                title = "Chapter",
                                children = listOf(Section(id = 4, title = "Sub", children = listOf(text(5))), text(6)),
                            ),
                        ),
                ),
                Page(id = 7, title = null, children = listOf(text(8))),
            )

        assertEquals(
            listOf(
                PageRow(1, "Page", number = 1, pageCount = 2),
                QuestionRow(text(2), "1", depth = 0, group = 2),
                SectionRow(3, "Chapter", "2", depth = 0, group = 3),
                SectionRow(4, "Sub", "2.1", depth = 1, group = 3),
                QuestionRow(text(5), "2.1.1", depth = 2, group = 3),
                QuestionRow(text(6), "2.2", depth = 1, group = 3),
                PageRow(7, null, number = 2, pageCount = 2),
                QuestionRow(text(8), "1", depth = 0, group = 8),
            ),
            form.toRows(),
        )
    }

    @Test
    fun toRows_keepsChildrenOfUnsupportedItems() {
        val form =
            listOf(
                Unsupported(id = 1, rawType = "wizard", children = listOf(text(2))),
                Page(
                    id = 3,
                    title = "Page",
                    children = listOf(Unsupported(id = 4, rawType = "group", children = listOf(text(5)))),
                ),
            )

        assertEquals(
            listOf(
                UnsupportedRow(1, depth = 0, group = 1, isTopLevel = true),
                QuestionRow(text(2), "1", depth = 0, group = 2),
                PageRow(3, "Page", number = 1, pageCount = 1),
                UnsupportedRow(4, depth = 0, group = 4, isTopLevel = false),
                QuestionRow(text(5), "1.1", depth = 1, group = 4),
            ),
            form.toRows(),
        )
    }

    @Test
    fun summary_tracksEachChoiceInOrder_andAddsUpSelectedPoints() {
        val form =
            listOf(
                Page(
                    id = 1,
                    title = "Page",
                    children =
                        listOf(
                            choice(2, selected = setOf(21, 22)),
                            Section(3, "Chapter", listOf(choice(4, emptySet()), text(5), choice(6, setOf(61)))),
                        ),
                ),
            )

        val summary = form.summary()

        assertEquals(FormSummary(answered = listOf(true, false, true), score = 4), summary)
        assertEquals(2, summary.answeredCount)
        assertEquals(3, summary.total)
    }

    @Test
    fun railPosition_keepsGroupHeadingsOffTheRail() {
        val heading = SectionRow(2, "A", "1", depth = 0, group = 2)
        val first = QuestionRow(text(3), "1.1", depth = 1, group = 2)
        val middle = QuestionRow(text(4), "1.2", depth = 1, group = 2)
        val last = QuestionRow(text(5), "1.3", depth = 1, group = 2)
        val loose = QuestionRow(text(6), "2", depth = 0, group = 6)

        assertEquals(
            RailPosition(onRail = false, first = true, last = false),
            railPosition(PageRow(1, "P"), heading, first),
        )
        assertEquals(RailPosition(onRail = true, first = true, last = false), railPosition(heading, first, middle))
        assertEquals(RailPosition(onRail = true, first = false, last = false), railPosition(first, middle, last))
        assertEquals(RailPosition(onRail = true, first = false, last = true), railPosition(middle, last, loose))
        assertEquals(RailPosition(onRail = true, first = true, last = true), railPosition(last, loose, null))
    }
}
