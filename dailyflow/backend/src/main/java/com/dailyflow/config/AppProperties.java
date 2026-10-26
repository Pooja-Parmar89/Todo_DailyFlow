package com.dailyflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dailyflow")
public record AppProperties(
        @DefaultValue("UTC") String timezone,
        Jwt jwt,
        Cors cors,
        Mail mail) {

    public record Jwt(String secret, @DefaultValue("60") long expirationMinutes) {
    }

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    public record Mail(@DefaultValue("no-reply@dailyflow.local") String from) {
    }
}
