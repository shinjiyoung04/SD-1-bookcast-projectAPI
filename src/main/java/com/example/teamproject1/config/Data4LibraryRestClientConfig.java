package com.example.teamproject1.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class Data4LibraryRestClientConfig {

    @Bean
    @Qualifier("data4LibraryRestClient")
    public RestClient data4LibraryRestClient(
            @Value("${data4library.connect-timeout-ms:5000}")
            int connectTimeoutMs,

            @Value("${data4library.read-timeout-ms:30000}")
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
                .requestFactory(requestFactory)
                .build();
    }
}
