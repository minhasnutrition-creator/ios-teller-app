package android.content

class ClipData(val description: Any?, val item: Any?) {
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData =
            ClipData(label, text)
    }
}

class ClipboardManager {
    fun setPrimaryClip(clip: ClipData?) {}
}
