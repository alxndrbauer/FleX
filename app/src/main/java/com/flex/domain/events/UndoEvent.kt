package com.flex.domain.events

data class UndoEvent(val message: String, val undoAction: suspend () -> Unit)
