package com.danapps.nativewebrtc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import io.socket.client.Socket
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

class MainActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var rtcClient: RtcClient
    private lateinit var mSocket: Socket

    private val offerSdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            val offer = Gson().toJson(p0, SessionDescription::class.java)
            Log.d("MyApplication", "Main Sending Offer")
            mSocket.emit("offer", offer)
        }
    }

    private val answerSdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
            Log.d("MyApplication", "Main Sending Answer")
            val answer = Gson().toJson(p0, SessionDescription::class.java)
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
            ) + ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initLocal()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) or ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            ) -> {
                AlertDialog.Builder(this).setTitle("Permissions Needed")
                    .setMessage("Audio And Video Permissions Are Required To Use This App")
                    .setPositiveButton("GRANT") { d, _ ->
                        d.dismiss()
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            ), 121
                        )
                    }
                    .setNegativeButton("Close The App") { d, _ ->
                        d.dismiss()
                        finish()
                    }
                    .create()
                    .show()
            }
            else -> {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ), 121
                )
            }
        }
    }

    private fun initLocal() {
        Toast.makeText(this, "Starting To Initiate", Toast.LENGTH_SHORT).show()


        mSocket = (application as Application).mSocket

        mSocket.on("offer") {
            Log.d("MyApplication", "Main RECEIVED Offer")
            runOnUiThread {
                val remoteOffer = Gson().fromJson(it[0].toString(), SessionDescription::class.java)
                rtcClient.onRemoteSessionReceived(remoteOffer, object : AppSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        rtcClient.answer(answerSdpObserver)
                    }
                })
            }
        }


        mSocket.on("answer") {
            runOnUiThread {
                val remoteOffer = Gson().fromJson(it[0].toString(), SessionDescription::class.java)
                rtcClient.onRemoteSessionReceived(remoteOffer, null)
            }
        }

        mSocket.on("ice") {
            runOnUiThread {
                val ice = Gson().fromJson(it[0].toString(), IceCandidate::class.java)
                rtcClient.addIceCandidate(ice)
            }
        }

        rtcClient = RtcClient(application, object : PeerConnectionObserver() {

            //IceCandidate
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                Log.d("MyApplication", "onIceCandidate: ")
                val ice = Gson().toJson(p0, IceCandidate::class.java)
                mSocket.emit("ice", ice)
                rtcClient.addIceCandidate(p0)
            }

            //Got Stream
            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                Log.d("MyApplication", "onAddStream: ")
                p0?.videoTracks?.get(0)?.addSink(remoteVideo)
//                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
//                Log.d("MyApplication", "onAddStream: ${audioManager.isSpeakerphoneOn}")
//                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
//                audioManager.isSpeakerphoneOn = true
//                Log.d("MyApplication", "onAddStream: ${audioManager.isSpeakerphoneOn}")

            }
        })

        //SetUp Views And Local Stream
        rtcClient.initSurfaceView(localVideo)
        rtcClient.initSurfaceView(remoteVideo)
        rtcClient.setLocalStream(localVideo)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 121 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            initLocal()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.call_action -> {
                if (v.tag.equals("0")) {
                    v.tag = "1"
                    rtcClient.call(offerSdpObserver)
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_call_end
                        )
                    )
                } else {
                    v.tag = "0"
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_call
                        )
                    )
                    rtcClient.end()
                }
            }
            R.id.video_action -> {
                if (v.tag.equals("0")) {
                    v.tag = "1"
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_videocam_off
                        )
                    )
                    rtcClient.removeVideo(localVideo)
                } else {
                    v.tag = "0"
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_videocam_on
                        )
                    )
                    rtcClient.addVideo(localVideo)
                }
            }
            R.id.audio_action -> {
                if (v.tag.equals("0")) {
                    v.tag = "1"
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_mic_off
                        )
                    )
                    rtcClient.removeAudio()
                } else {
                    v.tag = "0"
                    (v as FloatingActionButton).setImageDrawable(
                        ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_mic_on
                        )
                    )
                    rtcClient.addAudio()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rtcClient.close()
    }
}