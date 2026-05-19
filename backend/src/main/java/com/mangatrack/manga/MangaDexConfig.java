package com.mangatrack.manga;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MangaDexProperties.class)
public class MangaDexConfig {

    @Bean
    public RestClient mangaDexRestClient(RestClient.Builder builder, MangaDexProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public MangaDexService.Sleeper mangaDexSleeper() {
        return MangaDexService.Sleeper.real();
    }
}
