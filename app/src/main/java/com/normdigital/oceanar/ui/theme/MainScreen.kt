package com.normdigital.oceanar.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.webrtc.SessionDescription

@Composable
fun MainScreen(
    onCreateOffer: () -> Unit,
    onJoinRoom: (SessionDescription) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onCreateOffer,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Create Offer")
        }

        Button(
            onClick = {
                // Placeholder for testing Join Room functionality
                val testSdp = SessionDescription(SessionDescription.Type.OFFER, "test_sdp")
                onJoinRoom(testSdp)
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Join Room")
        }
    }
}
