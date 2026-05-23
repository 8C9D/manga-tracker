package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioNotificationServiceTest {

    private static final String ACCOUNT_SID = "AC123";
    private static final String AUTH_TOKEN = "test-token";
    private static final String FROM_NUMBER = "+15550000000";
    private static final String EXPECTED_URI =
            "https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json";

    private MockRestServiceServer server;
    private TwilioNotificationService service;

    private final User user = new User("Alice", "+12025550101");
    private final Manga manga = new Manga("Naruto");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TwilioNotificationService(builder, ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER);
    }

    @Test
    void send_postsFormEncodedRequestToAccountMessagesEndpoint() {
        String expectedBasicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes(StandardCharsets.ISO_8859_1));

        server.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicAuth))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "To", "+12025550101",
                        "From", FROM_NUMBER,
                        "Body", "New chapter alert! Naruto — Chapter 101 is now available on MangaDex."
                )))
                .andRespond(withSuccess());

        assertThatCode(() -> service.send(user, manga, "101")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void send_serverError_throws() {
        server.expect(requestTo(EXPECTED_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> service.send(user, manga, "101"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void send_clientError_throws() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.send(user, manga, "101"))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }
}
