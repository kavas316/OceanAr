package com.normdigital.oceanar

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import com.google.firebase.firestore.DocumentChange

class FirebaseSignalingClient(
    private val firestore: FirebaseFirestore,
    private val signalingClient: SignalingClient,
    private val roomId: String = "room1" // Varsayılan oda ismi
) {

    private val callRef = firestore.collection("calls").document(roomId)
    private var answerListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null

    // Teklif (Offer) gönderme
    fun sendOffer(sdp: SessionDescription) {
        val offerMap = mapOf(
            "sdp" to sdp.description,
            "type" to sdp.type.toString().lowercase()
        )
        callRef.set(mapOf("offer" to offerMap))
            .addOnSuccessListener {
                Log.d("FirebaseSignalingClient", "Offer başarıyla gönderildi: $offerMap")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSignalingClient", "Offer gönderilirken hata oluştu: ${e.message}")
            }
    }

    // ICE candidate gönderme (sadece offer tarafı için)
    fun sendIceCandidate(candidate: IceCandidate) {
        val candidateMap = mapOf(
            "candidate" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        val callRef = firestore.collection("calls").document("room1")
        callRef.collection("offerCandidates").add(candidateMap)
            .addOnSuccessListener {
                Log.d("FirebaseSignalingClient", "ICE Candidate başarıyla gönderildi: $candidateMap")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSignalingClient", "ICE Candidate gönderilirken hata oluştu: ${e.message}")
            }
    }

    fun startListeningForIceCandidates() {
        val callRef = firestore.collection("calls").document("room1")
        callRef.collection("answerCandidates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) {
                Log.e("FirebaseSignalingClient", "ICE Candidate dinlenirken hata oluştu: ${e?.message}")
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
                    signalingClient.onIceCandidateReceived(candidate)
                }
            }
        }
    }



    // Dinleyicileri başlatma (sadece answer tarafını dinler)
    fun startListening() {
        // Cevap (Answer) dinleyicisi
        callRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                Log.e("FirebaseSignalingClient", "Answer dinlenirken hata oluştu: ${e?.message}")
                return@addSnapshotListener
            }

            val data = snapshot.data
            val answer = data?.get("answer") as? Map<*, *>
            if (answer != null) {
                val sdp = answer["sdp"] as? String
                val type = answer["type"] as? String
                if (sdp != null && type != null) {
                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        sdp
                    )
                    signalingClient.onAnswerReceived(sessionDescription)
                }
            }
        }

        // ICE adaylarını dinleme
        callRef.collection("answerCandidates")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    Log.e("FirebaseSignalingClient", "Answer ICE Candidate dinlenirken hata oluştu: ${e?.message}")
                    return@addSnapshotListener
                }

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val candidateData = change.document.data
                        val candidate = IceCandidate(
                            candidateData["sdpMid"] as? String ?: "",
                            (candidateData["sdpMLineIndex"] as? Long)?.toInt() ?: -1,
                            candidateData["candidate"] as? String ?: ""
                        )
                        signalingClient.onIceCandidateReceived(candidate)
                    }
                }
            }
    }


    fun sendAnswer(sdp: SessionDescription) {
        val answerMap = mapOf(
            "sdp" to sdp.description,
            "type" to sdp.type.toString()
        )
        val callRef = firestore.collection("calls").document("room1")
        callRef.update(mapOf("answer" to answerMap))
            .addOnSuccessListener {
                Log.d("FirebaseSignalingClient", "Answer başarıyla gönderildi: $answerMap")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSignalingClient", "Answer gönderilirken hata oluştu: ${e.message}")
            }
    }


    // Dinleyicileri durdurma
    fun stopListening() {
        answerListener?.remove()
        candidateListener?.remove()
        Log.d("FirebaseSignalingClient", "Dinleyiciler kapatıldı.")
    }

    // Bağlantıyı sonlandırma
    fun closeConnection() {
        stopListening()
        Log.d("FirebaseSignalingClient", "Bağlantı kapatıldı.")
    }
}

