package com.normdigital.oceanar

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignalingClient {
    fun onOfferReceived(sdp: SessionDescription)
    fun onAnswerReceived(sdp: SessionDescription)
    fun onIceCandidateReceived(candidate: IceCandidate)
}
