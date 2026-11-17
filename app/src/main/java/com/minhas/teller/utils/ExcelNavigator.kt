package com.minhas.teller.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ExcelNavigator {
    data class ExcelEvent(val filePath: String, val title: String)

    private val _events = MutableSharedFlow<ExcelEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ExcelEvent> = _events.asSharedFlow()

    fun open(filePath: String, title: String) {
        _events.tryEmit(ExcelEvent(filePath, title))
    }
}
