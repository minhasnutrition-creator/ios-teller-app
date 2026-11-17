package androidx.activity.result.contract

object ActivityResultContracts {
    class RequestPermission
    class RequestMultiplePermissions
    class GetContent
    class StartActivityForResult
    class StartIntentSenderForResult
    class PickVisualMedia {
        companion object {
            val ImageOnly = Any()
            val VideoOnly = Any()
            val ImageAndVideo = Any()
        }
    }
}
