package com.klef.ps024.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klef.ps024.vehicle.repository.VehicleRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vehicledb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class VehicleServiceIntegrationTest {

    private static final String ADMIN_TOKEN = "admin@example.com";
    private static final String STAFF_TOKEN = "staff@example.com";
    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private SecretKey jwtSigningKey;

    @BeforeEach
    void clearVehicles() {
        vehicleRepository.deleteAll();
    }

    @Test
    void createsVehicleAndSetsLocationAndTimestamps() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("ab-123", "ACTIVE")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.registrationNumber").value("AB-123"))
                .andExpect(jsonPath("$.make").value("Toyota"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(vehicleRepository.findById(response.get("id").asLong())).isPresent();
    }

    @Test
    void retrievesAllVehiclesForStaff() throws Exception {
        createVehicle("AB-123", "ACTIVE");
        createVehicle("CD-456", "INACTIVE");

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void retrievesVehicleById() throws Exception {
        long id = createVehicle("AB-123", "ACTIVE");

        mockMvc.perform(get("/api/vehicles/{id}", id)
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.registrationNumber").value("AB-123"));
    }

    @Test
    void updatesVehicle() throws Exception {
        long id = createVehicle("AB-123", "ACTIVE");

        mockMvc.perform(put("/api/vehicles/{id}", id)
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("XY-987", "UNDER_MAINTENANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("XY-987"))
                .andExpect(jsonPath("$.status").value("UNDER_MAINTENANCE"))
                .andExpect(jsonPath("$.assignedDriver").value("Casey Driver"));
    }

    @Test
    void deletesVehicle() throws Exception {
        long id = createVehicle("AB-123", "ACTIVE");

        mockMvc.perform(delete("/api/vehicles/{id}", id)
                        .header("Authorization", bearerToken("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(vehicleRepository.existsById(id)).isFalse();
    }

    @Test
    void returnsNotFoundForMissingVehicle() throws Exception {
        mockMvc.perform(get("/api/vehicles/{id}", 987654L)
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle with id 987654 was not found."));
    }

    @Test
    void rejectsBlankFieldsInvalidYearAndUnknownStatus() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationNumber": " ",
                                  "make": "",
                                  "model": "Corolla",
                                  "year": 1800,
                                  "status": "BROKEN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.registrationNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.make").exists())
                .andExpect(jsonPath("$.fieldErrors.year").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void rejectsDuplicateRegistrationNumbersRegardlessOfCase() throws Exception {
        createVehicle("AB-123", "ACTIVE");

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("ab-123", "ACTIVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A vehicle with registration number AB-123 already exists."));
    }

    @Test
    void requiresAuthenticationForDirectVehicleRequests() throws Exception {
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsStaffReadsButRestrictsWritesToAdmins() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("AB-123", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsJwtWithInvalidSignature() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET));

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", bearerToken("STAFF", wrongKey, Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", bearerToken("STAFF", jwtSigningKey, Instant.now().minusSeconds(60))))
                .andExpect(status().isUnauthorized());
    }

    private long createVehicle(String registrationNumber, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(registrationNumber, status)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String vehicleJson(String registrationNumber, String status) {
        return """
                {
                  "registrationNumber": "%s",
                  "make": "Toyota",
                  "model": "Corolla",
                  "year": 2022,
                  "status": "%s",
                  "assignedDriver": "Casey Driver"
                }
                """.formatted(registrationNumber, status);
    }

    private String bearerToken(String role) {
        return bearerToken(role, jwtSigningKey, Instant.now().plusSeconds(600));
    }

    private String bearerToken(String role, SecretKey signingKey, Instant expiresAt) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        String token = Jwts.builder()
                .subject(role.equals("ADMIN") ? ADMIN_TOKEN : STAFF_TOKEN)
                .claim("roles", List.of(role))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return "Bearer " + token;
    }
}
