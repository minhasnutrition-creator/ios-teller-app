package android.app

class DatePickerDialog(
    val context: Any?,
    val listener: ((view: Any?, year: Int, month: Int, dayOfMonth: Int) -> Unit)? = null,
    val year: Int = 2026,
    val month: Int = 0,
    val dayOfMonth: Int = 1
) {
    val datePicker = android.widget.DatePicker()

    fun interface OnDateSetListener {
        fun onDateSet(view: Any?, year: Int, month: Int, dayOfMonth: Int)
    }

    fun setTitle(title: CharSequence) {}

    fun show() {
        // Multiplatform date picker dialog
        listener?.invoke(null, year, month, dayOfMonth)
    }
}
