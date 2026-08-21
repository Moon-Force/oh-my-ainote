package com.moonforce.ohmyainote.document.undo

import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.TextRecord

fun interface StateWriter<S> {
    suspend fun write(state: S)
}

interface UndoableCommand<S> {
    fun apply(state: S): S
    fun revert(state: S): S
}

class UndoStack<S>(private val capacity: Int = 80) {
    private val undo = ArrayDeque<UndoableCommand<S>>()
    private val redo = ArrayDeque<UndoableCommand<S>>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    suspend fun apply(state: S, command: UndoableCommand<S>, writer: StateWriter<S>): S {
        val next = command.apply(state)
        writer.write(next)
        undo.addLast(command)
        while (undo.size > capacity) undo.removeFirst()
        redo.clear()
        return next
    }

    suspend fun undo(state: S, writer: StateWriter<S>): S {
        val command = undo.removeLastOrNull() ?: return state
        val next = command.revert(state)
        writer.write(next)
        redo.addLast(command)
        return next
    }

    suspend fun redo(state: S, writer: StateWriter<S>): S {
        val command = redo.removeLastOrNull() ?: return state
        val next = command.apply(state)
        writer.write(next)
        undo.addLast(command)
        return next
    }
}

sealed interface PageCommand : UndoableCommand<PageSnapshot> {
    data class AddStrokes(val records: List<StrokeRecord>) : PageCommand {
        override fun apply(state: PageSnapshot) = state.withStrokes(state.strokes + records)
        override fun revert(state: PageSnapshot) = state.withStrokes(state.strokes.filterNot { candidate -> records.any { it.id == candidate.id } })
    }

    data class RemoveStrokes(val records: List<StrokeRecord>) : PageCommand {
        override fun apply(state: PageSnapshot) = state.withStrokes(state.strokes.filterNot { candidate -> records.any { it.id == candidate.id } })
        override fun revert(state: PageSnapshot) = state.withStrokes(state.strokes + records)
    }

    data class InsertCard(val card: AiCardRecord) : PageCommand {
        override fun apply(state: PageSnapshot) = state.copy(page = state.page.copy(cards = state.page.cards + card))
        override fun revert(state: PageSnapshot) = state.copy(page = state.page.copy(cards = state.page.cards.filterNot { it.id == card.id }))
    }

    data class DeleteCard(val card: AiCardRecord) : PageCommand {
        override fun apply(state: PageSnapshot) = state.copy(page = state.page.copy(cards = state.page.cards.filterNot { it.id == card.id }))
        override fun revert(state: PageSnapshot) = state.copy(page = state.page.copy(cards = state.page.cards + card))
    }

    data class ReplaceStrokesWithText(val records: List<StrokeRecord>, val text: TextRecord) : PageCommand {
        override fun apply(state: PageSnapshot): PageSnapshot {
            val ids = records.map { it.id }.toSet()
            val remaining = state.strokes.filterNot { it.id in ids }
            val next = state.withStrokes(remaining)
            return next.copy(page = next.page.copy(texts = next.page.texts + text))
        }

        override fun revert(state: PageSnapshot): PageSnapshot {
            val next = state.withStrokes(state.strokes + records)
            return next.copy(page = next.page.copy(texts = next.page.texts.filterNot { it.id == text.id }))
        }
    }

    data class RemoveTexts(val texts: List<TextRecord>) : PageCommand {
        override fun apply(state: PageSnapshot) = state.copy(page = state.page.copy(texts = state.page.texts.filterNot { candidate -> texts.any { it.id == candidate.id } }))
        override fun revert(state: PageSnapshot) = state.copy(page = state.page.copy(texts = state.page.texts + texts))
    }
}

private fun PageSnapshot.withStrokes(value: List<StrokeRecord>) = copy(
    page = page.copy(strokes = value.map { it.copy(points = emptyList()) }),
    strokes = value,
)
