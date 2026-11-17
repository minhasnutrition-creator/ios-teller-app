package androidx.lifecycle

import android.app.Application

open class AndroidViewModel(private val application: Application = Application()) : ViewModel() {
    open fun getApplication(): Application = application
}
