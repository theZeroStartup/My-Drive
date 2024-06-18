package com.zero.drive.base

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
abstract class BaseActivity: AppCompatActivity() {

    /**
     * @param message Messages to be shown on UI
     */
    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}