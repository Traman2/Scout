package com.scout.springserver.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.scout.springserver.util.Timestamps;
import java.io.IOException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serializes every timestamp exactly like JSON.stringify(new Date()) does in
 * the Express server ("2025-01-01T12:00:00.000Z"), so the frontend sees the
 * same format from either backend.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer instantAsJsDate() {
        return builder -> builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(Timestamps.toIso(value));
            }
        });
    }
}
