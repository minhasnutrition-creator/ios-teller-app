package java.lang.ref

class WeakReference<T : Any>(referent: T?) {
    private var ref: T? = referent
    fun get(): T? = ref
    fun clear() {
        ref = null
    }
}
