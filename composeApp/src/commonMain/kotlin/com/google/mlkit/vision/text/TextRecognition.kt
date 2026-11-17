package com.google.mlkit.vision.text

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

class Text(
    val text: String = "",
    val textBlocks: List<TextBlock> = emptyList()
) {
    class TextBlock(
        val text: String = "",
        val lines: List<Line> = emptyList()
    )
    class Line(
        val text: String = "",
        val elements: List<Element> = emptyList()
    )
    class Element(val text: String = "")
}

interface TextRecognizer {
    fun process(image: InputImage): Task<Text> = Task()
    fun close() {}
}

object TextRecognition {
    fun getClient(options: Any? = null): TextRecognizer = object : TextRecognizer {}
}
