package com.danapps.nativewebrtc

import android.app.Application
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket

class Application : Application() {

     lateinit var mSocket: Socket

    override fun onCreate() {
        super.onCreate()
        try {
//            mSocket = IO.socket("http://10.0.2.2:3000")
//            mSocket = IO.socket("http://192.168.0.102:3000")
            mSocket = IO.socket("https://web-rtc-native.herokuapp.com/")
            mSocket.connect()
        } catch (e: Exception) {
            Log.d("MyApplication", "onCreate: $e")
        }
    }
}