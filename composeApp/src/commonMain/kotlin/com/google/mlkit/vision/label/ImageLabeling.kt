package com.google.mlkit.vision.label

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class ImageLabel(
    val text: String = "",
    val confidence: Float = 0f,
    val index: Int = 0
)

interface ImageLabeler {
    fun process(image: InputImage): Task<List<ImageLabel>> = Task()
    fun close() {}
}

object ImageLabeling {
    fun getClient(options: ImageLabelerOptions): ImageLabeler = object : ImageLabeler {}
}
