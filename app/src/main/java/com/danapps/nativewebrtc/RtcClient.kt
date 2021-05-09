package com.danapps.nativewebrtc

import android.app.Application
import android.content.Context
import android.util.Log
import org.webrtc.*

class RtcClient(application: Application, observer: PeerConnection.Observer) {


    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
    }


    init {
        initPeerConnectionFactory(application)
    }

    private val rootEglBase: EglBase = EglBase.create()

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val cameraVideoCapturer by lazy { getVideoCapturer(application) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }


    //Step 1
    //Init PeerConnectionFactory
    private fun initPeerConnectionFactory(application: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }


    //Step 2
    //Build PeerConnectionFactory
    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    //Step 3
    //Get Local Video
    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    //Step 4
    //Init PeerConnection
    private fun buildPeerConnection(observer: PeerConnection.Observer) =
        peerConnectionFactory.createPeerConnection(
            iceServer,
            observer
        )

    //Method To InitSurface (Local and Remote View)
    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    //Set LocalStream On
    fun setLocalStream(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (cameraVideoCapturer as VideoCapturer).initialize(
            surfaceTextureHelper,
            localVideoOutput.context,
            localVideoSource.capturerObserver
        )
        cameraVideoCapturer.startCapture(320, 240, 60)
        val localVideoTrack =
            peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        peerConnection?.addStream(localStream)
    }

    //Add Ice Candidate
    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }


    // PeerConnection Call
    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.d("MyApplication", "onSetFailure: Local $p0")
                    }

                    override fun onSetSuccess() {
                        Log.d("MyApplication", "onSetSuccess: Local")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.d("MyApplication", "onCreateSuccess: $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        Log.d("MyApplication", "answer: ")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.d("MyApplication", "onSetFailure:$p0")
                    }

                    override fun onSetSuccess() {
                        Log.d("MyApplication", "onSetSuccess: Answer Local D")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
                Log.d("MyApplication", "onCreateSuccess: In Client $p0")
                sdpObserver.onCreateSuccess(p0)
            }

            override fun onCreateFailure(p0: String?) {
                Log.d("MyApplication", "onCreateFailure: $p0")
            }
        }, constraints)
    }


    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)
    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)


    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.d("MyApplication", "onSetFailure: $p0")
            }

            override fun onSetSuccess() {
                Log.d("MyApplication", "onSetSuccess: ")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, sessionDescription)
    }

}