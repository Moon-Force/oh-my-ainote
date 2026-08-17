package com.moonforce.ohmyainote.document.undo

import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.StrokeRecord

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
}

private fun PageSnapshot.withStrokes(value: List<StrokeRecord>) = copy(
    page = page.copy(strokes = value.map { it.copy(points = emptyList()) }),
    strokes = value,
)
