package com.labo.anapath.common.notification;

import com.labo.anapath.common.exception.InvalidOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OurVoiceClientTest {

    private static final String ENDPOINT = "https://api.ourvoice.test/calls";
    private static final String SMS_ENDPOINT = "https://api.ourvoice.test/sms";
    private static final String TOKEN = "test-bearer-token";
    private static final String TO = "22997000001";
    private static final String AUDIO_URL = "https://audio.test/file.mp3";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private OurVoiceClient client;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new OurVoiceClient(restTemplate);
    }

    @Test
    @DisplayName("call - envoie POST avec Authorization Bearer et retourne data.id")
    void call_sendsRequestAndReturnsAppelId() {
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "{\"data\":{\"id\":\"appel-abc-123\"}}",
                        MediaType.APPLICATION_JSON));

        String appelId = client.call(ENDPOINT, TOKEN, TO, AUDIO_URL);

        assertThat(appelId).isEqualTo("appel-abc-123");
        mockServer.verify();
    }

    @Test
    @DisplayName("call - payload contient to et audio_url")
    void call_payloadContainsToAndAudioUrl() {
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"" + TO + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(AUDIO_URL)))
                .andRespond(withSuccess(
                        "{\"data\":{\"id\":\"appel-xyz\"}}",
                        MediaType.APPLICATION_JSON));

        client.call(ENDPOINT, TOKEN, TO, AUDIO_URL);

        mockServer.verify();
    }

    @Test
    @DisplayName("call - réponse 500 → InvalidOperationException")
    void call_serverError_throwsInvalidOperationException() {
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.call(ENDPOINT, TOKEN, TO, AUDIO_URL))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("OurVoice");
    }

    @Test
    @DisplayName("sms - envoie POST avec Authorization Bearer et sender_id fixe")
    void sms_sendsRequestWithCorrectHeaders() {
        mockServer.expect(requestTo(SMS_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("c7e219bb-aa98-49e4-a87d-71250babaf98")))
                .andRespond(withSuccess("{\"status\":\"sent\"}", MediaType.APPLICATION_JSON));

        client.sms(SMS_ENDPOINT, TOKEN, TO, "Message test");

        mockServer.verify();
    }

    @Test
    @DisplayName("sms - payload contient le numéro destinataire")
    void sms_payloadContainsRecipient() {
        mockServer.expect(requestTo(SMS_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"" + TO + "\"")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.sms(SMS_ENDPOINT, TOKEN, TO, "Corps du message");

        mockServer.verify();
    }

    @Test
    @DisplayName("sms - réponse 4xx → InvalidOperationException")
    void sms_clientError_throwsInvalidOperationException() {
        mockServer.expect(requestTo(SMS_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.sms(SMS_ENDPOINT, TOKEN, TO, "Message"))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("OurVoice");
    }
}
