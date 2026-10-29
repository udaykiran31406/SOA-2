package com.klef.ps024.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfiguration {

    @Bean
    public RouteLocator serviceRoutes(RouteLocatorBuilder routes) {
        return routes.routes()
                .route("auth-service", route -> route
                        .path("/api/auth/**")
                        .uri("lb://auth-service"))
                .route("vehicle-service", route -> route
                        .path("/api/vehicles/**")
                        .uri("lb://vehicle-service"))
                .route("maintenance-service", route -> route
                        .path("/api/maintenance/**")
                        .uri("lb://maintenance-service"))
                .build();
    }
}
