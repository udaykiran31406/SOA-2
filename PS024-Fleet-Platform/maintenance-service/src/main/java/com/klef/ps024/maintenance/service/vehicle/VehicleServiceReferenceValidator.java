package com.klef.ps024.maintenance.service.vehicle;

import com.klef.ps024.maintenance.exception.VehicleNotFoundException;
import com.klef.ps024.maintenance.exception.VehicleServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VehicleServiceReferenceValidator implements VehicleReferenceValidator {

    private final RestClient restClient;
    private final String vehicleServiceUrl;

    public VehicleServiceReferenceValidator(
            RestClient.Builder loadBalancedRestClientBuilder,
            @Value("${vehicle-service.url:http://vehicle-service}") String vehicleServiceUrl) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.vehicleServiceUrl = vehicleServiceUrl;
    }

    @Override
    public void assertVehicleExists(Long vehicleId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("A validated JWT is required to verify the vehicle reference.");
        }

        try {
            restClient.get()
                    .uri(vehicleServiceUrl + "/api/vehicles/{id}", vehicleId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuthentication.getToken().getTokenValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new VehicleNotFoundException(vehicleId);
        } catch (RestClientException exception) {
            throw new VehicleServiceUnavailableException(exception);
        }
    }
}
