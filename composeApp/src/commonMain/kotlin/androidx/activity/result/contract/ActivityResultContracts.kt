package androidx.activity.result.contract

import android.net.Uri
import android.content.Intent

abstract class ActivityResultContract<I, O>

object ActivityResultContracts {
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class TakePicture : ActivityResultContract<Uri, Boolean>()
    class GetContent : ActivityResultContract<String, Uri?>()
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class StartActivityForResult : ActivityResultContract<Intent, androidx.activity.result.ActivityResult>()
    class StartIntentSenderForResult : ActivityResultContract<Any?, androidx.activity.result.ActivityResult>()
    class PickVisualMedia : ActivityResultContract<Any?, Uri?>() {
        companion object {
            val ImageOnly = Any()
            val VideoOnly = Any()
            val ImageAndVideo = Any()
        }
    }
}
