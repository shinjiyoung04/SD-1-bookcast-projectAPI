package com.example.teamproject1.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BookcastAiRestClientConfig {

    @Bean
    @Qualifier("bookcastAiRestClient")
    public RestClient bookcastAiRestClient(
            @Value("${bookcast.ai.base-url:http://localhost:8000}")
            String baseUrl,
            @Value("${bookcast.ai.connect-timeout-ms:3000}")
            int connectTimeoutMs,
            @Value("${bookcast.ai.read-timeout-ms:30000}")
            int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Math.max(1000, connectTimeoutMs)
        );

        requestFactory.setReadTimeout(
                Math.max(1000, readTimeoutMs)
        );

        return RestClient
                .builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
