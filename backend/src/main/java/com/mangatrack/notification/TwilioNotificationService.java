package com.mangatrack.notification;

import com.mangatrack.manga.Manga;
import com.mangatrack.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class TwilioNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TwilioNotificationService.class);

    private final RestClient restClient;
    private final String fromNumber;
    private final String accountSid;

    public TwilioNotificationService(
            RestClient.Builder builder,
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.from-number:}") String fromNumber) {
        this.accountSid = accountSid;
        this.fromNumber = fromNumber;
        this.restClient = builder
                .baseUrl("https://api.twilio.com")
                .defaultHeaders(h -> h.setBasicAuth(accountSid, authToken))
                .build();
    }

    @Override
    public void send(User user, Manga manga, String chapter) {
        String message = "New chapter alert! %s — Chapter %s is now available on MangaDex."
                .formatted(manga.getTitle(), chapter);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", user.getPhoneNumber());
        body.add("From", fromNumber);
        body.add("Body", message);

        restClient.post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("SMS sent to {} for '{}' ch {}", user.getPhoneNumber(), manga.getTitle(), chapter);
    }
}
