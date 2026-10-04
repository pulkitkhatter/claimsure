package com.claimsure.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT resource-server defaults (OWASP: deny by default, no sessions, no CSRF cookie flow for a bearer-token API,
 * method-level role checks via @PreAuthorize).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SecretKey jwtSecretKey(JwtProperties props) {
        return new SecretKeySpec(props.secret().getBytes(), "HmacSHA256");
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(SecretKey key, JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    /** Only identity-service really uses the encoder, but it is cheap and keeps signing/verification in one place. */
    @Bean
    @ConditionalOnMissingBean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityAutoConfiguration::rolesToAuthorities);
        return converter;
    }

    static Collection<GrantedAuthority> rolesToAuthorities(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<GrantedAuthority> out = new ArrayList<>();
        if (roles != null) {
            roles.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }
        return out;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProperties props,
                                            Converter<Jwt, AbstractAuthenticationToken> converter) throws Exception {
        String[] open = props.publicPaths().toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentTypeOptions(c -> { })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(open).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
