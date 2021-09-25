package com.example.remoteshell

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class DisplayServerActivity : AppCompatActivity() {

    lateinit var directoryPathDisplayText : TextView
    lateinit var portDisplayEdit : EditText
    lateinit var allowedHostDisplayEdit : EditText
    lateinit var tokenDisplayEdit : EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_server)

        directoryPathDisplayText = findViewById(R.id.directoryPathDisplayText)
        portDisplayEdit = findViewById(R.id.portDisplayEdit)
        allowedHostDisplayEdit = findViewById(R.id.allowedHostDisplayEdit)
        tokenDisplayEdit = findViewById(R.id.tokenDisplayEdit)

        directoryPathDisplayText.text = intent.getStringExtra("directory")
        portDisplayEdit.setText(intent.getIntExtra("port", -1).toString())
        allowedHostDisplayEdit.setText(intent.getStringExtra("host"))
        tokenDisplayEdit.setText(intent.getStringExtra("token"))

    }

    fun onDisplayOkClick(view: View){
        finish()
    }
}