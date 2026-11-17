package androidx.activity.result

class IntentSenderRequest(val intentSender: Any? = null) {
    class Builder(val intentSender: Any?) {
        fun build(): IntentSenderRequest = IntentSenderRequest(intentSender)
    }
}

class ActivityResult(val resultCode: Int = 0, val data: Any? = null) {
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}

class PickVisualMediaRequest(val mediaType: Any? = null)
