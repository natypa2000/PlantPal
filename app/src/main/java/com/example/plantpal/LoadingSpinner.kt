package com.example.plantpal
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import com.example.plantpal.R

class LoadingSpinner(context: Context) {
    private val dialog: Dialog = Dialog(context)

    init {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.spinner_layout, null)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}