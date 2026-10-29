package com.klef.ps024.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klef.ps024.auth.entity.User;
import com.klef.ps024.auth.entity.UserRole;
import com.klef.ps024.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "security.jwt.expiration-ms=3600000"
})
@AutoConfigureMockMvc
class AuthServiceIntegrationTest {

    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private SecretKey jwtSigningKey;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesStaffUserAndNeverReturnsPasswordData() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"Fleet.User@Example.com","password":"SecurePass123","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("fleet.user@example.com"))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var savedUser = userRepository.findByEmail("fleet.user@example.com").orElseThrow();
        assertThat(savedUser.getRole().name()).isEqualTo("STAFF");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("SecurePass123");
        assertThat(passwordEncoder.matches("SecurePass123", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void loginReturnsSignedJwtWithRoleClaim() throws Exception {
        register("driver@example.com", "SecurePass123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"DRIVER@example.com","password":"SecurePass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        var jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("driver@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("STAFF");
        assertThat(jwtAuthenticationConverter.convert(jwt).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_STAFF");
    }

    @Test
    void trustedAdminAccountReceivesAdminAuthority() throws Exception {
        userRepository.saveAndFlush(new User(
                "admin@example.com",
                passwordEncoder.encode("AdminPass123"),
                UserRole.ADMIN));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"AdminPass123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(jwtAuthenticationConverter.convert(jwtDecoder.decode(token)).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        register("duplicate@example.com", "SecurePass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"DUPLICATE@example.com","password":"SecurePass456"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void invalidCredentialsReturnGenericUnauthorizedResponse() throws Exception {
        register("login@example.com", "SecurePass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"WrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void invalidRequestFieldsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void jwtDecoderRejectsWrongSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET));
        String token = signedToken(wrongKey, Instant.now().plusSeconds(600));

        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    void jwtDecoderRejectsExpiredToken() {
        String token = signedToken(jwtSigningKey, Instant.now().minusSeconds(120));

        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    @Test
    void protectedRequestsRejectInvalidBearerTokens() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET));
        String token = signedToken(wrongKey, Instant.now().plusSeconds(600));

        mockMvc.perform(get("/api/private")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationPayload(email, password))))
                .andExpect(status().isCreated());
    }

    private String signedToken(SecretKey key, Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now()) ? expiresAt.minusSeconds(600) : Instant.now();
        return Jwts.builder()
                .subject("test@example.com")
                .claim("roles", "STAFF")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private record RegistrationPayload(String email, String password) {
    }
}
