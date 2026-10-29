package com.klef.ps024.maintenance.service.vehicle;

import com.klef.ps024.maintenance.exception.VehicleNotFoundException;
import com.klef.ps024.maintenance.exception.VehicleServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VehicleServiceReferenceValidatorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsCallerTokenToTheVehicleService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VehicleServiceReferenceValidator validator =
                new VehicleServiceReferenceValidator(builder, "http://vehicle-service");
        setAuthentication("maintenance-access-token");

        server.expect(requestTo("http://vehicle-service/api/vehicles/12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer maintenance-access-token"))
                .andRespond(withSuccess());

        validator.assertVehicleExists(12L);
        server.verify();
    }

    @Test
    void mapsVehicleNotFoundResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VehicleServiceReferenceValidator validator =
                new VehicleServiceReferenceValidator(builder, "http://vehicle-service");
        setAuthentication("maintenance-access-token");
        server.expect(requestTo("http://vehicle-service/api/vehicles/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> validator.assertVehicleExists(404L))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessage("Vehicle with id 404 was not found.");
        server.verify();
    }

    @Test
    void mapsVehicleServiceFailureAsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VehicleServiceReferenceValidator validator =
                new VehicleServiceReferenceValidator(builder, "http://vehicle-service");
        setAuthentication("maintenance-access-token");
        server.expect(requestTo("http://vehicle-service/api/vehicles/12"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> validator.assertVehicleExists(12L))
                .isInstanceOf(VehicleServiceUnavailableException.class);
        server.verify();
    }

    private void setAuthentication(String token) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .subject("staff@example.com")
                .claim("roles", List.of("STAFF"))
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
