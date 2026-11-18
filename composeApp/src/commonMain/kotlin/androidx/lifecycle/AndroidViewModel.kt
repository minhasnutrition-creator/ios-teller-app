package androidx.lifecycle

import android.app.Application

open class AndroidViewModel(private val application: Application = Application()) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    open fun <T : Application> getApplication(): T = application as T
}
