package com.google.mlkit.nl.entityextraction

import com.google.android.gms.tasks.Task

class Entity(val type: Int = 0) {
    companion object {
        const val TYPE_DATE_TIME = 1
        const val TYPE_MONEY = 2
    }
}

class EntityAnnotation(
    val annotatedText: String = "",
    val start: Int = 0,
    val end: Int = 0,
    val entities: List<Entity> = emptyList()
)

class EntityExtractionParams {
    class Builder(val text: String) {
        fun build(): EntityExtractionParams = EntityExtractionParams()
    }
}

class EntityExtractorOptions {
    class Builder(val modelIdentifier: String) {
        fun build(): EntityExtractorOptions = EntityExtractorOptions()
    }
    companion object {
        const val ENGLISH = "en"
    }
}

interface EntityExtractor {
    fun downloadModelIfNeeded(): com.google.android.gms.tasks.Task<Any?> = com.google.android.gms.tasks.Task<Any?>()
    fun annotate(params: EntityExtractionParams): com.google.android.gms.tasks.Task<List<EntityAnnotation>> = com.google.android.gms.tasks.Task<List<EntityAnnotation>>()
    fun close() {}
}

object EntityExtraction {
    fun getClient(options: EntityExtractorOptions): EntityExtractor = object : EntityExtractor {}
}
