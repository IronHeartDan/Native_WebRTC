package com.danapps.nativewebrtc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import io.socket.client.Socket
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class MainActivity : AppCompatActivity() {

    lateinit var rtcClient: RtcClient
    private lateinit var mSocket: Socket

    private val offerSdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            val offer = Gson().toJson(p0, SessionDescription::class.java)
            Log.d("MyApplication", "Main onCreateSuccess: $offer")
            mSocket.emit("offer", offer)
        }
    }

    private val answerSdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            Log.d("MyApplication", "onCreateSuccess: Answer Created")
            val answer = Gson().toJson(p0, SessionDescription::class.java)
            Log.d("MyApplication", "onCreateSuccess: Answer Created $answer")
            mSocket.emit("answer", answer)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        when {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                initLocal()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                AlertDialog.Builder(this).setTitle("Camera Permission Needed")
                    .setMessage("Camera Is Required To Use This App")
                    .setPositiveButton("GRANT") { d, _ ->
                        d.dismiss()
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), 121)
                    }
                    .setNegativeButton("Close The App") { d, _ ->
                        d.dismiss()
                        finish()
                    }
                    .create()
                    .show()
            }
            else -> {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 121)
            }
        }
    }

    private fun initLocal() {
        mSocket = (application as Application).mSocket

        mSocket.on("offer") {
            Log.d("MyApplication", "initLocal: Offer")
            runOnUiThread {
                val remoteOffer = Gson().fromJson(it[0].toString(), SessionDescription::class.java)
                Log.d("MyApplication", "initLocal: Offer $remoteOffer")
                rtcClient.onRemoteSessionReceived(remoteOffer)
                rtcClient.answer(answerSdpObserver)
            }
        }


        mSocket.on("answer") {
            runOnUiThread {
                val remoteOffer = Gson().fromJson(it[0].toString(), SessionDescription::class.java)
                rtcClient.onRemoteSessionReceived(remoteOffer)
            }
        }

        Toast.makeText(this, "Starting To Initiate", Toast.LENGTH_SHORT).show()
        rtcClient = RtcClient(application, object : PeerConnectionObserver() {

            //IceCandidate
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                rtcClient.addIceCandidate(p0)
            }

            //Got Stream
            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.videoTracks?.get(0)?.addSink(remoteVideo)
            }
        })

        //SetUp Views And Local Stream
        rtcClient.initSurfaceView(localVideo)
        rtcClient.initSurfaceView(remoteVideo)
        rtcClient.setLocalStream(localVideo)
        call.setOnClickListener { rtcClient.call(offerSdpObserver) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 121 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initLocal()
        }
    }
}