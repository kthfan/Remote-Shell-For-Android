package com.example.remoteshell

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.lang.Exception
import java.security.AccessControlException
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class SecondActivity : AppCompatActivity() , BackgroundService.Callbacks {
    val TAG = "SecondActivity"
    lateinit var portEdit:EditText
    lateinit var allowedHostEdit:EditText
    lateinit var workingDir : String
    lateinit var serviceIntent: Intent
    var serviceInstance : BackgroundService? = null
    lateinit var token : String
    private val mConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {

            // We've binded to LocalService, cast the IBinder and get LocalService instance
            val binder: BackgroundService.LocalBinder = service as BackgroundService.LocalBinder
            serviceInstance = binder.getService() //Get instance of your service!
            serviceInstance!!.registerClient(this@SecondActivity) //Activity register in the service as client for callabcks!
            try{

                if(askForPermission()) serviceInstance!!.runServer(workingDir, Arrays.asList("127.0.0.1", "localhost", allowedHostEdit.text.toString()), Arrays.asList(portEdit.text.toString().toInt()))
            }catch (e : AccessControlException){
                Toast.makeText(this@SecondActivity, "Working directory access denied.", Toast.LENGTH_LONG).show()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {

        }
    } 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        workingDir = intent.getStringExtra("destDir") as String
//            getExternalFilesDir("")?.absolutePath.toString()//intent.getStringExtra("destDir")
        Log.e(TAG, "working dir is: " + workingDir)
        portEdit = findViewById(R.id.port)
        allowedHostEdit = findViewById(R.id.allowedHost)

    }

    fun askForPermission(): Boolean{
        var writable = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
        var readable = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
        if( !writable){
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1)
        }
        if(!readable){
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1)
        }
        return writable && readable
    }

    fun onStartClick(view : View){
        serviceIntent = Intent(this, BackgroundService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE); //Binding to the service!
    }
    fun onTestClick(view : View){
        if(serviceInstance == null || serviceInstance?.fileServer == null){
            Toast.makeText(this, "Please start server first.", Toast.LENGTH_LONG).show()
            return
        }
        serviceInstance!!.runTestServer()
        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setData(Uri.parse("http://localhost:" + serviceInstance!!.testServerPort))
        startActivity(intent)
    }

    fun onStopClick(view : View){
        if(serviceInstance != null){
            serviceInstance!!.stopServer()
            serviceInstance!!.stopTestServer()
        }else Toast.makeText(this@SecondActivity, "Server not open yet.", Toast.LENGTH_LONG).show()

        try{
            unbindService(mConnection);
            stopService(serviceIntent);
        }catch (e : Exception){
            Toast.makeText(this@SecondActivity, "Server already closed.", Toast.LENGTH_LONG).show()
        }

    }


    override fun tokenCallback(token: String){
        this.token = token
        Log.v(TAG, token )
        findViewById<EditText>(R.id.displayToken).text = Editable.Factory.getInstance().newEditable(token);
    }

}