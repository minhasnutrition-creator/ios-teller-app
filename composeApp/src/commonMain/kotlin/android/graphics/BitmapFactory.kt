package android.graphics

import java.io.InputStream

object BitmapFactory {
    class Options {
        var inSampleSize: Int = 1
        var inJustDecodeBounds: Boolean = false
        var outWidth: Int = 0
        var outHeight: Int = 0
    }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    fun decodeStream(stream: InputStream?, outPadding: Any? = null, opts: Options? = null): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    fun decodeFile(pathName: String, opts: Options? = null): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
}
