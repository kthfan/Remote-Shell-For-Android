package com.example.remoteshell

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var destDir : String? = this.getDir1()

        val openDirectoryBn:Button = findViewById(R.id.openDirectoryBn)
        val toStep2Bn:Button = findViewById(R.id.toStep2Bn)
        val directoryPath:TextView =  findViewById(R.id.directoryPath)

        val directoryLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
            ActivityResultCallback {
                destDir = FileUtils.getFullPathFromTreeUri(it, this)
                directoryPath.text = destDir
            }
        )

        openDirectoryBn.setOnClickListener {
            directoryLauncher.launch(Uri.fromFile(getExternalFilesDir(null)))
        }
        toStep2Bn.setOnClickListener{
            if(destDir == null) {
                Toast.makeText(this, "Invalid path.", Toast.LENGTH_LONG).show()
            }else{
                val i = Intent(this, SecondActivity::class.java).apply {
                    putExtra("destDir", destDir)
                }
                startActivity(i)
            }
        }
        directoryPath.text = getExternalFilesDir(null)?.absolutePath


//        val i = Intent(this, SecondActivity::class.java).apply {
//            putExtra("destDir", "destDir")
//        }
//        startActivity(i)
    }


    fun getDir1(): String? {
        return getExternalFilesDir("")?.absolutePath
    }

    fun getDir2() : String{
        var s = packageName
        val p = packageManager.getPackageInfo(s!!, 0)
        s = p.applicationInfo.dataDir
        return s
    }
}