package com.google.mlkit.vision.common

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

class InputImage private constructor() {
    companion object {
        fun fromFilePath(context: Context, imageUri: Uri): InputImage = InputImage()
        fun fromBitmap(bitmap: Bitmap, rotationDegrees: Int = 0): InputImage = InputImage()
        fun fromMediaImage(image: Any?, rotationDegrees: Int): InputImage = InputImage()
    }
}
