package com.claimsure.policy.config;

import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.event.KafkaEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import java.util.List;
import org.bson.types.Decimal128;
import org.springframework.core.convert.converter.Converter;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableCaching
public class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventPublisher eventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                  @Value("${claimsure.topics.policy-events:policy-events}") String topic) {
        return new KafkaEventPublisher(kafka, mapper, topic);
    }

    /** Store money as Decimal128 instead of strings so it can be summed/sorted in MongoDB. */
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new ToDecimal128(), new FromDecimal128()));
    }

    @org.springframework.data.convert.WritingConverter
    static class ToDecimal128 implements Converter<java.math.BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(java.math.BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @org.springframework.data.convert.ReadingConverter
    static class FromDecimal128 implements Converter<Decimal128, java.math.BigDecimal> {
        @Override
        public java.math.BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }
}
