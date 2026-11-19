package com.example.serviciokafkaadmin.Config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Vendedores: Microsoft Entra ID
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String entraIssuer;

    @Value("${gamebakes.security.audience}")
    private String entraAudience;

    // Clientes: AWS Cognito
    @Value("${gamebakes.security.cognito.issuer-uri}")
    private String cognitoIssuer;

    @Value("${gamebakes.security.cognito.client-id}")
    private String cognitoClientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // El health check no necesita token
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationManagerResolver(authenticationManagerResolver())
            );

        return http.build();
    }

    /**
     * Acepta tokens de dos emisores. Segun el claim "iss" del token se elige
     * con que decoder se valida (firma, expiracion, issuer y audience).
     */
    private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        managers.put(entraIssuer, managerFor(entraIssuer, entraAudience));
        managers.put(cognitoIssuer, managerFor(cognitoIssuer, cognitoClientId));

        AuthenticationManagerResolver<String> porEmisor = managers::get;
        return new JwtIssuerAuthenticationManagerResolver(porEmisor);
    }

    private AuthenticationManager managerFor(String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();

        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator));

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        return provider::authenticate;
    }
}

/**
 * Valida que el claim "aud" del token coincida con la audiencia esperada
 * (evita aceptar tokens emitidos para otra aplicacion).
 */
class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
            "invalid_token",
            "El token no fue emitido para esta audiencia (aud)",
            null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}