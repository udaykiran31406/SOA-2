# Architecture Overview

The platform uses separate Spring Boot services for authentication, vehicles, and maintenance records. The reactive API Gateway is the client-facing entry point, while the Eureka server provides service discovery and Spring Cloud LoadBalancer resolves service instances. Auth, Vehicle, and Maintenance Services share the PostgreSQL database `fleet_platform_db` and use separate tables. Connection settings are supplied through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; Hibernate schema generation remains disabled.

| Component | Port | Database |
|---|---:|---|
| API Gateway | 8080 | — |
| Auth Service | 8081 | `fleet_platform_db` |
| Vehicle Service | 8082 | `fleet_platform_db` |
| Maintenance Service | 8083 | `fleet_platform_db` |
| Eureka Server | 8762 | — |

The vehicle service can be started a second time on port 8084 using the same application and a port override. No duplicate project is needed. Each service registers with `http://localhost:8762/eureka/`. Spring Cloud 2025.0.3 is used with Spring Boot 3.5.4.

Auth Service issues HS256 JWTs. The Gateway validates token signatures and expiry, permits only auth registration/login without a token, and routes `/api/auth/**`, `/api/vehicles/**`, and `/api/maintenance/**` to the corresponding Eureka service IDs without rewriting paths. It forwards the caller's Authorization header. Vehicle Service independently validates tokens and permits ADMIN/STAFF reads with ADMIN-only writes. Maintenance Service independently validates tokens, permits ADMIN/STAFF create/read/update, and restricts deletion to ADMIN. It validates vehicle references through a Eureka load-balanced call to Vehicle Service, forwarding the caller's token. Gateway tests use local mock HTTP backends and mock service discovery; database service tests use H2 in PostgreSQL compatibility mode, and Maintenance tests mock the Vehicle Service client. Live PostgreSQL, Eureka, and end-to-end service integration remain unverified.
