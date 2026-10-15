package com.telehealth.realtime;

import com.telehealth.realtime.repository.ChatMessageRepository;
import com.telehealth.realtime.repository.RoomRepository;
import com.telehealth.realtime.service.AppointmentRoomContextClient;
import com.telehealth.realtime.service.RealtimeCryptoService;
import com.telehealth.realtime.service.RealtimeService;
import com.telehealth.realtime.service.RealtimeTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WebRtcConfigTests {

    private RealtimeService service(String udpUrl, String tcpUrl) {
        RealtimeService service = new RealtimeService(mock(RoomRepository.class),
                mock(ChatMessageRepository.class), mock(RealtimeCryptoService.class),
                mock(RealtimeTokenService.class), mock(AppointmentRoomContextClient.class),
                mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "turnUrl", udpUrl);
        ReflectionTestUtils.setField(service, "turnTcpUrl", tcpUrl);
        ReflectionTestUtils.setField(service, "turnUsername", "fixture-user");
        ReflectionTestUtils.setField(service, "turnCredential", "fixture-password");
        return service;
    }

    @Test
    void includesUdpAndTcpRelayCandidatesWithMatchingCredentials() {
        var config = service("turn:relay.example.com:3478?transport=udp",
                "turn:relay.example.com:3478?transport=tcp").getWebRtcConfig();

        assertEquals(2, config.getIceServers().size());
        assertEquals(Map.of(
                "urls", List.of("turn:relay.example.com:3478?transport=udp",
                        "turn:relay.example.com:3478?transport=tcp"),
                "username", "fixture-user", "credential", "fixture-password"),
                config.getIceServers().get(1));
    }

    @Test
    void omitsBlankOrDuplicateFallbackUrls() {
        String url = "turn:relay.example.com:3478";
        assertEquals(List.of(url), service(url, " ").getWebRtcConfig().getIceServers().get(1).get("urls"));
        assertEquals(List.of(url), service(url, url).getWebRtcConfig().getIceServers().get(1).get("urls"));
        assertEquals(List.of(url), service(url, null).getWebRtcConfig().getIceServers().get(1).get("urls"));
    }

    @Test
    void doesNotExposeAnEmptyTurnServerWhenRelayUrlsAreAbsent() {
        assertEquals(1, service(null, " ").getWebRtcConfig().getIceServers().size());
    }
}
