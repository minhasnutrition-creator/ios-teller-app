package android.widget

import com.minhas.teller.PlatformUtils

class Toast(private val text: String = "") {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        fun makeText(context: Any?, text: CharSequence, duration: Int): Toast {
            return Toast(text.toString())
        }

        fun makeText(context: Any?, resId: Int, duration: Int): Toast {
            return Toast(resId.toString())
        }
    }

    fun show() {
        PlatformUtils.showToast(text)
    }
}
