package com.klef.ps024.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureWebTestClient
class ApiGatewayIntegrationTest {

    private static final String SIGNING_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(SIGNING_SECRET));
    private static final SecretKey OTHER_SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET));

    private static HttpServer authBackend;
    private static HttpServer vehicleBackend;
    private static HttpServer maintenanceBackend;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @DynamicPropertySource
    static void configureBackends(DynamicPropertyRegistry registry) {
        authBackend = startBackend("auth");
        vehicleBackend = startBackend("vehicle");
        maintenanceBackend = startBackend("maintenance");
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
                () -> backendUri(authBackend));
        registry.add("spring.cloud.discovery.client.simple.instances.vehicle-service[0].uri",
                () -> backendUri(vehicleBackend));
        registry.add("spring.cloud.discovery.client.simple.instances.maintenance-service[0].uri",
                () -> backendUri(maintenanceBackend));
    }

    @AfterAll
    static void stopBackends() {
        stop(authBackend);
        stop(vehicleBackend);
        stop(maintenanceBackend);
    }

    @Test
    void startsWithTheThreeExplicitLoadBalancedServiceRoutes() {
        List<String> routes = routeLocator.getRoutes()
                .map(route -> route.getId() + "=" + route.getUri())
                .collectList()
                .block();

        assertThat(routes).containsExactlyInAnyOrder(
                "auth-service=lb://auth-service",
                "vehicle-service=lb://vehicle-service",
                "maintenance-service=lb://maintenance-service");
    }

    @Test
    void routesPublicRegistrationAndLoginWithoutAuthentication() {
        webTestClient.post().uri("/api/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("auth POST /api/auth/register auth=false");

        webTestClient.post().uri("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("auth POST /api/auth/login auth=false");
    }

    @Test
    void rejectsMissingMalformedWrongSignatureAndExpiredTokens() {
        webTestClient.get().uri("/api/vehicles")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/vehicles")
                .headers(headers -> headers.setBearerAuth("not-a-jwt"))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/vehicles")
                .headers(headers -> headers.setBearerAuth(token("STAFF", OTHER_SIGNING_KEY, Instant.now().plusSeconds(600))))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/maintenance")
                .headers(headers -> headers.setBearerAuth(token("STAFF", SIGNING_KEY, Instant.now().minusSeconds(60))))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void routesAuthenticatedBusinessRequestsToTheMatchingBackendsWithoutRewritingPaths() {
        String staffToken = token("STAFF", SIGNING_KEY, Instant.now().plusSeconds(600));

        webTestClient.get().uri("/api/vehicles/12")
                .headers(headers -> headers.setBearerAuth(staffToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("vehicle GET /api/vehicles/12 auth=true");

        webTestClient.get().uri("/api/maintenance/34")
                .headers(headers -> headers.setBearerAuth(staffToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("maintenance GET /api/maintenance/34 auth=true");
    }

    @Test
    void forwardsCallerTokenToDownstreamServices() {
        String staffToken = token("STAFF", SIGNING_KEY, Instant.now().plusSeconds(600));

        webTestClient.get().uri("/api/vehicles")
                .headers(headers -> headers.setBearerAuth(staffToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("vehicle GET /api/vehicles auth=true");
    }

    @Test
    void preservesDownstreamServiceUnavailableResponses() {
        webTestClient.get().uri("/api/vehicles/unavailable")
                .headers(headers -> headers.setBearerAuth(
                        token("ADMIN", SIGNING_KEY, Instant.now().plusSeconds(600))))
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    private static String token(String role, SecretKey key, Instant expiresAt) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        return Jwts.builder()
                .subject(role.toLowerCase() + "@example.com")
                .claim("roles", List.of(role))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private static HttpServer startBackend(String serviceName) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                int status = exchange.getRequestURI().getPath().endsWith("/unavailable") ? 503 : 200;
                byte[] body = (serviceName + " " + exchange.getRequestMethod() + " "
                        + exchange.getRequestURI() + " auth="
                        + exchange.getRequestHeaders().containsKey("Authorization"))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(status, body.length);
                try (var responseBody = exchange.getResponseBody()) {
                    responseBody.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start the test backend for " + serviceName + ".", exception);
        }
    }

    private static String backendUri(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void stop(HttpServer server) {
        if (server != null) {
            server.stop(0);
        }
    }
}
