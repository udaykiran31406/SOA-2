package com.klef.ps024.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klef.ps024.maintenance.exception.VehicleNotFoundException;
import com.klef.ps024.maintenance.exception.VehicleServiceUnavailableException;
import com.klef.ps024.maintenance.repository.MaintenanceRepository;
import com.klef.ps024.maintenance.service.vehicle.VehicleReferenceValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:maintenancedb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class MaintenanceServiceIntegrationTest {

    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MaintenanceRepository maintenanceRepository;

    @Autowired
    private SecretKey jwtSigningKey;

    @MockitoBean
    private VehicleReferenceValidator vehicleReferenceValidator;

    @BeforeEach
    void clearRecords() {
        maintenanceRepository.deleteAll();
        reset(vehicleReferenceValidator);
    }

    @Test
    void createsRecordAndSetsLocationAndTimestamps() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("SCHEDULED", null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.vehicleId").value(12))
                .andExpect(jsonPath("$.serviceType").value("PREVENTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(maintenanceRepository.findById(response.get("id").asLong())).isPresent();
        verify(vehicleReferenceValidator).assertVehicleExists(12L);
    }

    @Test
    void listsAllRecordsForStaff() throws Exception {
        createRecord("SCHEDULED", null);
        createRecord("IN_PROGRESS", null);

        mockMvc.perform(get("/api/maintenance")
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void retrievesRecordById() throws Exception {
        long id = createRecord("SCHEDULED", null);

        mockMvc.perform(get("/api/maintenance/{id}", id)
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.vehicleId").value(12));
    }

    @Test
    void updatesRecordForStaffAndValidatesVehicleReference() throws Exception {
        long id = createRecord("SCHEDULED", null);
        String updatedRequest = """
                {
                  "vehicleId": 15,
                  "serviceType": "REPAIR",
                  "description": "Replaced brake pads",
                  "scheduledDate": "2026-11-15",
                  "completedDate": "2026-11-16",
                  "cost": 250.00,
                  "status": "COMPLETED"
                }
                """;

        mockMvc.perform(put("/api/maintenance/{id}", id)
                        .header("Authorization", bearerToken("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(15))
                .andExpect(jsonPath("$.serviceType").value("REPAIR"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedDate").value("2026-11-16"));

        verify(vehicleReferenceValidator).assertVehicleExists(15L);
    }

    @Test
    void deletesRecordForAdmin() throws Exception {
        long id = createRecord("SCHEDULED", null);

        mockMvc.perform(delete("/api/maintenance/{id}", id)
                        .header("Authorization", bearerToken("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(maintenanceRepository.existsById(id)).isFalse();
    }

    @Test
    void returnsNotFoundForMissingRecord() throws Exception {
        mockMvc.perform(get("/api/maintenance/{id}", 987654L)
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Maintenance record with id 987654 was not found."));
    }

    @Test
    void rejectsInvalidFieldsAndUnsupportedServiceTypeAndStatus() throws Exception {
        mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": 0,
                                  "serviceType": "WASH",
                                  "description": " ",
                                  "scheduledDate": null,
                                  "completedDate": null,
                                  "cost": -1,
                                  "status": "BROKEN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleId").exists())
                .andExpect(jsonPath("$.fieldErrors.serviceType").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.scheduledDate").exists())
                .andExpect(jsonPath("$.fieldErrors.cost").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void rejectsCompletionWithoutCompletionDateAndInvalidDateOrder() throws Exception {
        mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPLETED", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.completedDate").exists());

        mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("COMPLETED", "2026-11-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.completedDate").exists());
    }

    @Test
    void rejectsNonexistentVehicleReference() throws Exception {
        doThrow(new VehicleNotFoundException(12L))
                .when(vehicleReferenceValidator).assertVehicleExists(12L);

        mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("SCHEDULED", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle with id 12 was not found."));
    }

    @Test
    void returnsServiceUnavailableWhenVehicleServiceCannotValidateReference() throws Exception {
        doThrow(new VehicleServiceUnavailableException(new IllegalStateException("unavailable")))
                .when(vehicleReferenceValidator).assertVehicleExists(12L);

        mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("SCHEDULED", null)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void requiresAuthenticationAndRejectsBadOrExpiredTokens() throws Exception {
        mockMvc.perform(get("/api/maintenance"))
                .andExpect(status().isUnauthorized());

        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(OTHER_SECRET));
        mockMvc.perform(get("/api/maintenance")
                        .header("Authorization", bearerToken("STAFF", wrongKey, Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/maintenance")
                        .header("Authorization", bearerToken("STAFF", jwtSigningKey, Instant.now().minusSeconds(60))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteRecords() throws Exception {
        long id = createRecord("SCHEDULED", null);

        mockMvc.perform(delete("/api/maintenance/{id}", id)
                        .header("Authorization", bearerToken("STAFF")))
                .andExpect(status().isForbidden());

        assertThat(maintenanceRepository.existsById(id)).isTrue();
    }

    private long createRecord(String status, String completedDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/maintenance")
                        .header("Authorization", bearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(status, completedDate)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String requestJson(String status, String completedDate) {
        return """
                {
                  "vehicleId": 12,
                  "serviceType": "PREVENTIVE",
                  "description": "Scheduled oil and filter service",
                  "scheduledDate": "2026-11-15",
                  "completedDate": %s,
                  "cost": 125.50,
                  "status": "%s"
                }
                """.formatted(completedDate == null ? "null" : "\"" + completedDate + "\"", status);
    }

    private String bearerToken(String role) {
        return bearerToken(role, jwtSigningKey, Instant.now().plusSeconds(600));
    }

    private String bearerToken(String role, SecretKey signingKey, Instant expiresAt) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        String token = Jwts.builder()
                .subject(role.toLowerCase() + "@example.com")
                .claim("roles", List.of(role))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return "Bearer " + token;
    }
}
