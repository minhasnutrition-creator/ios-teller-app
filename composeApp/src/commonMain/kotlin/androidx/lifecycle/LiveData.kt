package androidx.lifecycle

open class LiveData<T>(initialValue: T? = null) {
    open var value: T? = initialValue
        protected set
}

open class MutableLiveData<T>(initialValue: T? = null) : LiveData<T>(initialValue) {
    public override var value: T?
        get() = super.value
        set(value) { super.value = value }

    fun postValue(value: T?) {
        this.value = value
    }
}
