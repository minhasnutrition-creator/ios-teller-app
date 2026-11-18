package android.app

import android.widget.DatePicker

class DatePickerDialog(
    val context: Any?,
    val listener: OnDateSetListener? = null,
    val year: Int = 2026,
    val month: Int = 0,
    val dayOfMonth: Int = 1
) {
    val datePicker = DatePicker()

    fun interface OnDateSetListener {
        fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int)
    }

    private var onDismiss: (() -> Unit)? = null

    fun setTitle(title: CharSequence) {}
    fun setOnDismissListener(listener: (() -> Unit)?) {
        onDismiss = listener
    }

    fun show() {
        listener?.onDateSet(datePicker, year, month, dayOfMonth)
    }
}
