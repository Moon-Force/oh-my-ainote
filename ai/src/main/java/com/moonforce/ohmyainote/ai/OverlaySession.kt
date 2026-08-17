package com.moonforce.ohmyainote.ai

import com.moonforce.ohmyainote.ai.api.AiAnswer
import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.CardAnchor
import com.moonforce.ohmyainote.document.model.PageRect
import java.time.Instant
import java.util.UUID

data class OverlayTurn(val question: String, val answer: AiAnswer)

class OverlaySession(
    val pageId: String,
    val selection: PageRect,
    val region: RasterizedRegion,
) {
    private val mutableTurns = mutableListOf<OverlayTurn>()
    val turns: List<OverlayTurn> get() = mutableTurns.toList()

    fun add(question: String, answer: AiAnswer) {
        require(question.isNotBlank() && answer.text.isNotBlank())
        mutableTurns += OverlayTurn(question.trim(), answer)
    }

    fun currentCard(
        pageWidthPt: Float,
        pageHeightPt: Float,
        thumbPath: String,
        cardId: String = UUID.randomUUID().toString(),
    ): AiCardRecord {
        val turn = mutableTurns.lastOrNull() ?: error("No AI answer to insert")
        val width = 160f
        val height = 96f
        var x = selection.r + 8f
        var y = selection.t
        if (x + width > pageWidthPt) {
            x = selection.l.coerceAtMost(pageWidthPt - width)
            y = selection.b + 8f
        }
        if (y + height > pageHeightPt) {
            x = (pageWidthPt - width).coerceAtLeast(0f)
            y = (pageHeightPt - height).coerceAtLeast(0f)
        }
        return AiCardRecord(
            id = cardId,
            pageId = pageId,
            selection = selection,
            anchor = CardAnchor(x, y, width, height),
            question = turn.question,
            answer = turn.answer.text,
            thumbPath = thumbPath,
            model = turn.answer.model,
            createdAt = Instant.now().toString(),
        )
    }
}
