package com.minhas.teller.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PdfNavigator {
    data class PdfEvent(val filePath: String, val title: String, val voucherId: Int = -1)

    private val _events = MutableSharedFlow<PdfEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PdfEvent> = _events.asSharedFlow()

    /** [voucherId] lets the viewer print this bill on a thermal printer; -1 for other documents. */
    fun open(filePath: String, title: String, voucherId: Int = -1) {
        _events.tryEmit(PdfEvent(filePath, title, voucherId))
    }
}
