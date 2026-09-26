package com.mohdshayan.mutoscope.core.undo

/**
 * A bounded undo and redo history of immutable snapshots. The caller pushes the state that is
 * about to be replaced; undo hands back that state and remembers the current one for redo.
 * Once [capacity] steps are stored the oldest is dropped.
 */
class UndoModel<T>(val capacity: Int = 100) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size

    fun push(before: T) {
        undoStack.addLast(before)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        while (undoStack.size > capacity) undoStack.removeFirst()
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** Every snapshot still held, so the caller can keep the files they reference. */
    fun snapshots(): List<T> = undoStack.toList() + redoStack.toList()
}
