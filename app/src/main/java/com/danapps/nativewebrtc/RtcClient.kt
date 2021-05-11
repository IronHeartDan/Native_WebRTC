package com.danapps.nativewebrtc

import android.app.Application
import android.content.Context
import android.util.Log
import org.webrtc.*

class RtcClient(application: Application, observer: PeerConnection.Observer) {


    companion object {
        private const val VIDEO_TRACK_ID = "video_track"
        private const val AUDIO_TRACK_ID = "audio_track"
        private const val STREAM_ID = "local_track"
    }

    private lateinit var localStream: MediaStream
    private lateinit var audioTrack: AudioTrack
    private lateinit var videoTrack: VideoTrack


    init {
        initPeerConnectionFactory(application)
    }

    private val rootEglBase: EglBase = EglBase.create()

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:numb.viagenie.ca").setUsername("webrtc@live.com")
            .setPassword("muazkh").createIceServer()
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
        cameraVideoCapturer.startCapture(1024, 720, 60)

        videoTrack =
            peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource)
        videoTrack.addSink(localVideoOutput)


        audioTrack = peerConnectionFactory.createAudioTrack(
            AUDIO_TRACK_ID,
            peerConnectionFactory.createAudioSource(MediaConstraints())
        )


        localStream = peerConnectionFactory.createLocalMediaStream(STREAM_ID)

        localStream.addTrack(videoTrack)
        localStream.addTrack(audioTrack)

        peerConnection?.addStream(localStream)
    }

    fun removeVideo(localVideoOutput: SurfaceViewRenderer) {
        localStream.removeTrack(videoTrack)
        localVideoOutput.release()
    }

    fun addVideo(localVideoOutput: SurfaceViewRenderer) {
        localStream.addTrack(videoTrack)
    }

    fun removeAudio() {
        localStream.removeTrack(audioTrack)
    }

    fun addAudio() {
        localStream.removeTrack(audioTrack)
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
                        sdpObserver.onCreateSuccess(desc)
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.d("MyApplication", "Bla Bla Bla onCreateSuccess: $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        Log.d("MyApplication", "Local Generating answer: ")
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
                        Log.d("MyApplication", "onSetSuccess: Answer Local Set")
                        sdpObserver.onCreateSuccess(p0)
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.d("MyApplication", "Local onCreateSuccess: Answer")
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
            }

            override fun onCreateFailure(p0: String?) {
                Log.d("MyApplication", "onCreateFailure: $p0")
            }
        }, constraints)
    }

    private fun PeerConnection.end() {

    }


    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)
    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)
    fun end() = peerConnection?.end()
    fun close() = peerConnection?.close()


    fun onRemoteSessionReceived(sessionDescription: SessionDescription, sdpObserver: SdpObserver?) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.d("MyApplication", "onSetFailure: $p0")
            }

            override fun onSetSuccess() {
                Log.d("MyApplication", "Remote onSetSuccess: ")
                sdpObserver?.onSetSuccess()
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, sessionDescription)
    }

}