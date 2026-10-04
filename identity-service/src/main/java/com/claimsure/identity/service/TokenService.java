package com.claimsure.identity.service;

import com.claimsure.common.security.JwtProperties;
import com.claimsure.identity.domain.User;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenService(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", new ArrayList<>(user.getRoles()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long ttlSeconds() {
        return props.tokenTtl().toSeconds();
    }
}
