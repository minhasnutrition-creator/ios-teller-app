package android.os

object Environment {
    const val DIRECTORY_DOWNLOADS = "Download"
    const val DIRECTORY_DOCUMENTS = "Documents"
    const val DIRECTORY_PICTURES = "Pictures"

    fun getExternalStoragePublicDirectory(type: String): java.io.File = java.io.File(type)
}
