package com.example.remoteshell

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.lang.Exception
import java.security.AccessControlException
import java.util.*
import kotlin.collections.ArrayList


@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity::"

    private lateinit var addServerBn : Button
    private lateinit var deleteServerBn : Button
    private lateinit var displayServerBn : Button
    private lateinit var startServerBn : Button
    private lateinit var stopServerBn : Button
    private lateinit var startTextServerBn : Button
    private lateinit var serverListLayout : LinearLayout

    private var serverListCursor = -1
    private val serverList = ArrayList<FileServerEntry>()

    private val secondActivityResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.getData()
            val entry = FileServerEntry();
            entry.host =  data?.getStringExtra("host");
            entry.port =  data?.getIntExtra("port", 80);
            entry.workingDirectory =  data?.getStringExtra("directory");
            entry.token =  data?.getStringExtra("token");
            if(entry.token != null && entry.token != "") entry.hasDafaultToken = true
            serverList.add(entry);
            this.appendServerView(entry)
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            val binder: BackgroundService.LocalBinder = service as BackgroundService.LocalBinder
            serviceInstance = binder.getService()
            //serviceInstance!!.registerClient(this@MainActivity)

        }

        override fun onServiceDisconnected(arg0: ComponentName) {

        }
    }

    private var serviceInstance : BackgroundService? = null
    lateinit var serviceIntent : Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        this.addServerBn = findViewById(R.id.addServerBn)
        this.deleteServerBn = findViewById(R.id.deleteServerBn)
        this.displayServerBn = findViewById(R.id.displayServerBn)
        this.startServerBn = findViewById(R.id.startServerBn)
        this.stopServerBn = findViewById(R.id.stopServerBn)
        this.startTextServerBn = findViewById(R.id.startTextServerBn)
        this.serverListLayout = findViewById(R.id.serverListLayout)


        // start service
        serviceIntent = Intent(this, BackgroundService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE); //Binding to the service!

    }

    override fun onDestroy() {
        super.onDestroy()
        // destroy service
        try{
            unbindService(mConnection);
            stopService(serviceIntent);
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

    fun onAddServerClick(view: View){
        val intent = Intent(this, SecondActivity::class.java)

        secondActivityResultLauncher.launch(intent)
    }
    fun onDeleteServerClick(view: View){
        if(serverListCursor != -1){
            // stop server
            val serverEntry = serverList[serverListCursor]
            serviceInstance?.stopServer(serverEntry)
            serviceInstance?.stopTestServer(serverEntry)

            // remove
            serverListLayout.removeView(serverEntry.viewEntry)
            serverList.remove(serverEntry)
            if(serverList.size == 0) this.serverListCursor = -1;
            else if(serverListCursor > 0) this.serverListCursor--;

            // focus next entry
            if(serverList.size != 0) this.focusViewEntry(serverList[serverListCursor])
        }else{
            Toast.makeText(this@MainActivity, "No server have created.", Toast.LENGTH_LONG).show()
        }

    }
    fun onDisplayServerClick(view: View){
        if(serverListCursor != -1){
            val intent = Intent(this, DisplayServerActivity::class.java)
            val serverEntry = serverList[serverListCursor]
            intent.putExtra("directory", serverEntry.workingDirectory)
            intent.putExtra("port", serverEntry.port)
            intent.putExtra("host", serverEntry.host)
            intent.putExtra("token", serverEntry.token)
            startActivity(intent)
        }else{
            Toast.makeText(this@MainActivity, "No server have created.", Toast.LENGTH_LONG).show()
        }
    }

    fun onStartServerClick(view: View){
        if(!askForPermission()){
            Toast.makeText(this@MainActivity, "Need to request permission first, please try again.", Toast.LENGTH_LONG).show()
            return
        }
        if(serverListCursor != -1){
            try{
                if(serviceInstance?.runServer(serverList[serverListCursor]) == false){
                    Toast.makeText(this@MainActivity, "Server is already running.", Toast.LENGTH_LONG).show()
                }else serverList[serverListCursor].configRunning(true)
            }catch (e : AccessControlException){
                Toast.makeText(this@MainActivity, "Working directory access denied.", Toast.LENGTH_LONG).show()
            }
        }else{
            Toast.makeText(this@MainActivity, "No server have created.", Toast.LENGTH_LONG).show()
        }
    }
    fun onStopServerClick(view: View){
        if(serverListCursor != -1){
            if(serviceInstance?.stopServer(serverList[serverListCursor]) == false){
                Toast.makeText(this@MainActivity, "Server is already closed.", Toast.LENGTH_LONG).show()
            }else {
                serverList[serverListCursor].configRunning(false)
                serverList[serverListCursor].configTesting(false)
            }
            serviceInstance?.stopTestServer(serverList[serverListCursor])
        }else{
            Toast.makeText(this@MainActivity, "No server have created.", Toast.LENGTH_LONG).show()
        }
    }
    fun onTestServerClick(view: View){
        if(serverListCursor == -1){
            Toast.makeText(this@MainActivity, "No server have created.", Toast.LENGTH_LONG).show()
            return
        }
        if(serverList[serverListCursor].fileServer == null){
            Toast.makeText(this@MainActivity, "Must start server first.", Toast.LENGTH_LONG).show()
            return
        }
        if(serviceInstance?.runTestServer(serverList[serverListCursor]) == false){
//            Toast.makeText(this@MainActivity, "The Test server is already running.", Toast.LENGTH_LONG).show()
        }else{
            serverList[serverListCursor].configTesting(true)
        }
        // open browser
        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setData(Uri.parse("http://localhost:" + serverList[serverListCursor].testPort))
        startActivity(intent)
    }

    private fun appendServerView(fileServerEntry: FileServerEntry){
        val viewEntry : View = layoutInflater.inflate(
            R.layout.server_entry,
            this.serverListLayout,
            false
        )
        this.serverListLayout.addView(viewEntry)

        fileServerEntry.viewEntry = viewEntry
        fileServerEntry.configViewEntry()
        viewEntry.setOnClickListener{
            if(serverListCursor != -1){
                this.blurViewEntry(serverList[serverListCursor])
            }
            this.focusViewEntry(fileServerEntry)
            serverListCursor = this.serverList.indexOf(fileServerEntry)
        }

        // if not entry have been created before, then focus the created entry
        if(this.serverList.size == 1){
            this.focusViewEntry(fileServerEntry)
            serverListCursor = this.serverList.indexOf(fileServerEntry)
        }
    }

    private fun focusViewEntry(fileServerEntry: FileServerEntry){
        fileServerEntry?.viewEntry?.setBackgroundColor(Color.argb(50, 0, 150, 0))
    }
    private fun blurViewEntry(fileServerEntry: FileServerEntry){
        fileServerEntry?.viewEntry?.setBackgroundColor(Color.argb(0, 0, 0, 0))
    }

    private fun askForPermission(): Boolean{
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