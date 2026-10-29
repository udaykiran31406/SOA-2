# Project Requirements

PS024 is an automotive fleet maintenance and service operations platform built with Java 25, Spring Boot 3.5.4, Maven, Spring Cloud, PostgreSQL, and Spring Data JPA.

The system contains a Eureka discovery server, API gateway, authentication service, vehicle service, and maintenance service. Auth Service provides registration and JWT login. The reactive API Gateway provides HS256 JWT validation and Eureka/LoadBalancer routes for the Auth, Vehicle, and Maintenance Services. Vehicle Service provides JWT-protected vehicle CRUD with ADMIN write access and ADMIN/STAFF read access. Maintenance Service provides JWT-protected maintenance CRUD, ADMIN/STAFF create/read/update access, ADMIN-only deletion, and Vehicle Service reference validation through Eureka.

See the project [README](../README.md) for ports and local setup notes.
