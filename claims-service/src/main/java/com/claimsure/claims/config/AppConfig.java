package com.claimsure.claims.config;

import com.claimsure.claims.client.PolicyClient;
import com.claimsure.claims.client.RestPolicyClient;
import com.claimsure.common.event.EventPublisher;
import com.claimsure.common.event.KafkaEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.util.List;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    /** "http://policy-service" is resolved through Eureka by the load-balancer interceptor. */
    @Bean
    PolicyClient policyClient(@LoadBalanced RestClient.Builder builder,
                              @Value("${claimsure.policy-service.url:http://policy-service}") String url) {
        return new RestPolicyClient(builder, url);
    }

    @Bean
    EventPublisher eventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                  @Value("${claimsure.topics.claim-events:claim-events}") String topic) {
        return new KafkaEventPublisher(kafka, mapper, topic);
    }

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
