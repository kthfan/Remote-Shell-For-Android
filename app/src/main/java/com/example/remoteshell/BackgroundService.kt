package com.example.remoteshell

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.security.AccessControlException
import java.util.*
import kotlin.collections.RandomAccess

@RequiresApi(Build.VERSION_CODES.O)
open class BackgroundService : Service() {
    val TAG = "BackgroundService"
    val testServerPort = Random().nextInt(50000) + 10000;
    var activity: Callbacks? = null
    var fileServer: FileSystemServer? = null;
    var testServer: HttpServer? = null;
    var portToConnect:Int = 1234;
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): BackgroundService = this@BackgroundService
    }


    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "onCreate")
//        onStart()
    }

//    override fun onDestroy() {
//        super.onDestroy()
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return LocalBinder()
    }

    private fun injectPort(port:Int):String{
        return "<script>window.portToConnect=\"${port}\"</script>"
    }
    private fun injectToken(token:String):String{
        return "<script>document.querySelector(\"input\").value = \"${token}\"; setTimeout(()=>document.querySelector(\"button\").click(), 1000);</script>"
    }
    fun readIndexHtml(): String {
        val inputStream : InputStream = applicationContext.assets.open("index.html")
        val bufferedReader : BufferedReader = inputStream.bufferedReader()
        val stringBuilder = StringBuilder()
        var text:String? = bufferedReader.readLine()
        while (text != null) {
            stringBuilder.append(text);
            stringBuilder.append("\n");
            text = bufferedReader.readLine()
        }
        bufferedReader.close()
        stringBuilder.append(this.injectToken(fileServer!!.token))
        stringBuilder.append(this.injectPort(this.portToConnect))
        return stringBuilder.toString()
    }

    fun runServer(workingDir:String, allowedHost:Collection<String>, ports:Collection<Int>) {
        this.portToConnect = ports.firstOrNull()!!.toInt()

        fileServer = FileSystemServer(workingDir, null,  allowedHost + Arrays.asList("localhost:" + this.testServerPort),  ports)


        this.activity?.tokenCallback(fileServer!!.token)


        Thread{
            fileServer!!.start()
        }.start()


    }
    fun runTestServer(){
        Thread{
            this.testServer = object:HttpServer(Arrays.asList(this.testServerPort)){
                override fun onRequest(request: Request?, response: Response?, socket: Socket?) {
                    response?.setDuplicateHeader("Access-Control-Allow-Origin", "http://localhost");
                    response?.setDuplicateHeader("Content-Type", "text/html");
                    response?.setBodyByText(readIndexHtml())
                }
            }
            this.testServer!!.start()
        }.start()
    }

    fun stopTestServer(){
        testServer!!.close()
    }
    fun stopServer(){
        fileServer!!.close()
    }

    fun registerClient(activity: Activity){
        this.activity = activity as Callbacks
    }

    interface Callbacks {
        fun tokenCallback(token: String)

    }

}