package com.normdigital.oceanar.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*
import com.normdigital.oceanar.FirebaseSignalingClient
import com.normdigital.oceanar.SignalingClient
import com.google.firebase.firestore.DocumentChange


class WebRTCManager(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {

    lateinit var eglBaseContext: EglBase.Context
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private lateinit var signalingClient: FirebaseSignalingClient

    fun initialize() {
        eglBaseContext = EglBase.create().eglBaseContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        signalingClient = FirebaseSignalingClient(firestore, object : SignalingClient {
            override fun onOfferReceived(sdp: SessionDescription) {
                handleOfferReceived(sdp)
            }

            override fun onAnswerReceived(sdp: SessionDescription) {
                handleAnswerReceived(sdp)
            }

            override fun onIceCandidateReceived(candidate: IceCandidate) {
                handleIceCandidateReceived(candidate)
            }
        })

        createPeerConnection()
        signalingClient.startListening()

        // ICE adaylarını dinlemeye başla
        startListeningForIceCandidates()  // Burada ICE adaylarını dinlemeye başlatıyoruz
    }


    private fun handleOfferReceived(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTCManager", "Offer SDP başarıyla Remote Description olarak ayarlandı.")
                createAnswer()
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Offer SDP Remote Description olarak ayarlanamadı: $error")
            }

            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTCManager", "Answer SDP başarıyla ayarlandı.")
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTCManager", "Answer SDP ayarlanamadı: $error")
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sessionDescription)

                signalingClient.sendAnswer(sessionDescription)
                Log.d("WebRTCManager", "Answer created and sent: $sessionDescription")
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "Answer oluşturulamadı: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun startListeningForOfferAndAnswer() {
        val callRef = firestore.collection("calls").document("room1")

        // Teklif (Offer) dinleyicisi
        callRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                Log.e("WebRTCManager", "Offer dinlenirken hata oluştu: ${e?.message}")
                return@addSnapshotListener
            }

            val data = snapshot.data
            val offer = data?.get("offer") as? Map<*, *>
            if (offer != null) {
                val sdp = offer["sdp"] as? String
                val type = offer["type"] as? String
                if (sdp != null && type != null) {
                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        sdp
                    )
                    handleOfferReceived(sessionDescription)
                }
            }

            val answer = data?.get("answer") as? Map<*, *>
            if (answer != null) {
                val sdp = answer["sdp"] as? String
                val type = answer["type"] as? String
                if (sdp != null && type != null) {
                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        sdp
                    )
                    handleAnswerReceived(sessionDescription)
                }
            }
        }
    }


    private fun handleAnswerReceived(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTCManager", "Answer SDP başarıyla Remote Description olarak ayarlandı.")
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTCManager", "Answer SDP Remote Description olarak ayarlanamadı: $error")
            }

            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

    private fun handleIceCandidateReceived(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d("WebRTCManager", "ICE Candidate başarıyla eklendi.")
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    signalingClient.sendIceCandidate(it)  // ICE adayını Firebase'e gönderiyoruz.
                    Log.d("WebRTCManager", "ICE Candidate created: $it")
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                Log.w("WebRTCManager", "AddStream çağrıldı ancak Unified Plan kullanılıyor. Bu yöntem devre dışı.")
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                transceiver?.receiver?.track()?.let { track ->
                    Log.d("WebRTCManager", "Remote track received.")
                }
            }
        })

        // Video kaynağını oluştur
        val videoSource = peerConnectionFactory.createVideoSource(false)

        // Ses kaynağını oluştur
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())

        // Video track'ini oluştur ve PeerConnection'a ekle
        val videoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
        val audioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

        // Yerel video track'ini PeerConnection'a ekle
        peerConnection?.addTrack(videoTrack)
        peerConnection?.addTrack(audioTrack)

        Log.d("WebRTCManager", "Kamera ve mikrofon başarıyla başlatıldı.")
    }




    fun startWebcam(localView: SurfaceViewRenderer, remoteView: SurfaceViewRenderer) {
        try {
            // Video ve ses kaynaklarını oluştur
            val videoSource = peerConnectionFactory.createVideoSource(false)
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())

            // Kamera seçimi ve başlatılması
            val cameraEnumerator = Camera2Enumerator(context)
            val frontCamera = cameraEnumerator.deviceNames.firstOrNull { cameraEnumerator.isFrontFacing(it) }

            if (frontCamera == null) {
                Log.e("WebRTCManager", "Ön kamera bulunamadı!")
                return
            }

            val videoCapturer = cameraEnumerator.createCapturer(frontCamera, null)
            if (videoCapturer == null) {
                Log.e("WebRTCManager", "Video capturer oluşturulamadı!")
                return
            }

            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer.startCapture(640, 480, 30)

            // Video ve ses track'lerini oluşturma
            localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
            localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

            // Yerel video track'i görüntüye bağla
            localVideoTrack?.addSink(localView)

            // Video ve ses track'lerini PeerConnection'a ekle
            localVideoTrack?.let { videoTrack ->
                peerConnection?.addTrack(videoTrack)
            }

            localAudioTrack?.let { audioTrack ->
                peerConnection?.addTrack(audioTrack)
            }

            Log.d("WebRTCManager", "Kamera ve mikrofon başarıyla başlatıldı.")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Webcam başlatılırken hata oluştu: ${e.message}")
        }
    }


    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                // Local SDP'yi ayarla
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTCManager", "Local SDP başarıyla ayarlandı.")
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTCManager", "Local SDP ayarlanamadı: $error")
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sessionDescription)

                // Teklifi Firebase'e gönder
                signalingClient.sendOffer(sessionDescription)
                Log.d("WebRTCManager", "Offer created and sent: $sessionDescription")
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTCManager", "Offer oluşturulamadı: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }


    fun answerCall(roomId: String) {
        Log.d("WebRTCManager", "Answering call for room: $roomId")
    }
    fun startListeningForIceCandidates() {
        val callRef = firestore.collection("calls").document("room1")
        callRef.collection("answerCandidates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) {
                Log.e("WebRTCManager", "Answer ICE Candidate dinlenirken hata oluştu: ${e?.message}")
                return@addSnapshotListener
            }

            snapshot.documentChanges.forEach { change ->
                if (change.type == DocumentChange.Type.ADDED) {
                    val candidateData = change.document.data
                    val candidate = IceCandidate(
                        candidateData["sdpMid"] as? String ?: "",
                        (candidateData["sdpMLineIndex"] as? Long)?.toInt() ?: -1,
                        candidateData["candidate"] as? String ?: ""
                    )
                    peerConnection?.addIceCandidate(candidate) // Firebase'den alınan ICE adayını PeerConnection'a ekliyoruz.
                    Log.d("WebRTCManager", "ICE Candidate received and added: $candidate")
                }
            }
        }
    }


    fun hangup() {
        peerConnection?.close()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        Log.d("WebRTCManager", "Call ended.")
    }
}
