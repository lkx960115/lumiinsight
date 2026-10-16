package com.lumiinsight.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(LOCAL_DATE_TIME));
        module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
        return builder.modules(module)
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    static class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
        FlexibleLocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String raw = parser.getValueAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim().replace('T', ' ');
            int zone = indexOfZone(text);
            if (zone > 0) {
                text = text.substring(0, zone).trim();
            }
            if (text.length() >= 19) {
                text = text.substring(0, 19);
            }
            return LocalDateTime.parse(text, LOCAL_DATE_TIME);
        }

        private static int indexOfZone(String text) {
            int z = text.toUpperCase().indexOf('Z');
            if (z > 0) {
                return z;
            }
            int plus = text.indexOf('+', 10);
            if (plus > 0) {
                return plus;
            }
            int minus = text.indexOf('-', 10);
            return minus > 10 ? minus : -1;
        }
    }
}
