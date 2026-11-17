package com.google.mlkit.vision.barcode

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

interface BarcodeScanner {
    fun process(image: InputImage): Task<List<Barcode>> = Task()
    fun close() {}
}

object BarcodeScanning {
    fun getClient(options: Any? = null): BarcodeScanner = object : BarcodeScanner {}
}
