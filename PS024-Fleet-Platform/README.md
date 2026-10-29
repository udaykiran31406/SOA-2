# PS024 – Automotive Fleet Maintenance & Service Operations Platform

Java 25 / Spring Boot 3.5.4 Maven project for the PS024 college project.

## Services and ports

| Project | Port | Purpose |
|---|---:|---|
| `service-discovery-server` | 8762 | Eureka service registry |
| `api-gateway` | 8080 | JWT-protected, discovery-based API routing |
| `auth-service` | 8081 | Authentication and user persistence |
| `vehicle-service` | 8082 | JWT-protected vehicle management API |
| `maintenance-service` | 8083 | JWT-protected maintenance management API |

The vehicle service can be run a second time on port 8084 with the same project, for example:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8084"
```

Build and run the services with a Java 25 JDK and Maven 3.9 or later. Each service is an independent Maven project and targets Java 25.

Spring Cloud `2025.0.3` is the release train compatible with Spring Boot `3.5.x`. The five directories are independent Maven projects.

## Database setup

All database-backed services share a PostgreSQL database named `fleet_platform_db`; the users, vehicles, and maintenance records live in separate tables. Set `DB_USERNAME` and `DB_PASSWORD` for all services. `DB_URL` is optional and defaults to `jdbc:postgresql://localhost:5433/fleet_platform_db`. No password is stored in this repository. Hibernate schema generation is disabled, so create the schema before starting the services. The setup script uses the PostgreSQL `psql` client:

```powershell
$env:DB_USERNAME = "your-postgres-user"
psql -p 5433 -v ON_ERROR_STOP=1 -U $env:DB_USERNAME -d postgres -f .\database\schema.sql
```

`psql` will prompt for the database password. Keep the same database URL and credentials in each backend service process.

## API Gateway

The reactive API Gateway listens on port `8080`, registers with Eureka, and uses Spring Cloud LoadBalancer to route without hardcoded backend hosts or ports:

| Public path | Eureka service |
|---|---|
| `/api/auth/**` | `auth-service` |
| `/api/vehicles/**` | `vehicle-service` |
| `/api/maintenance/**` | `maintenance-service` |

`POST /api/auth/register` and `POST /api/auth/login` are public. Other requests require a valid Bearer JWT signed with HS256 using the same Base64-encoded `JWT_SECRET` as Auth Service. The Gateway verifies the signature and expiry; Vehicle and Maintenance Services continue to enforce their own role permissions. The Gateway forwards the original path and Authorization header to the selected service.

Set `JWT_SECRET` in the same terminal used to start the Gateway, then start Eureka and the backend services as required:

```powershell
Set-Location .\api-gateway
mvn spring-boot:run
```

Send requests through `http://localhost:8080`, for example `POST /api/auth/login` without a token, then use its `accessToken` as a Bearer token for `/api/vehicles` or `/api/maintenance`.

Gateway automated tests use local mock HTTP backends and mock service discovery configuration. They verify route selection, forwarded paths and tokens, authentication, and downstream `503` propagation; live Eureka and end-to-end service integration have not been verified.

## Auth Service

The Auth Service listens on port `8081` and registers with the Eureka server at `http://localhost:8762/eureka/`. Run the schema in [`database/schema.sql`](database/schema.sql) before starting it; the schema creates the `fleet_platform_db.users` table. Hibernate schema generation is disabled.

Set these variables in the same terminal used to start the service:

```powershell
$env:DB_USERNAME = "your-postgres-user"
$env:DB_PASSWORD = "your-postgres-password"
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:JWT_EXPIRATION_MS = "3600000"
```

`JWT_SECRET` must be a Base64-encoded key of at least 32 bytes. Keep it private and configure the same value in services that validate these access tokens. `JWT_EXPIRATION_MS` is optional and defaults to one hour. Never commit real environment values.

With JDK 25 and Maven 3.9 or later, start the discovery server first, then run:

```powershell
Set-Location .\auth-service
mvn spring-boot:run
```

### Register and log in

Send these requests from Postman to `http://localhost:8081`:

**Register** — `POST /api/auth/register`, Body → raw → JSON:

```json
{
  "email": "staff@example.com",
  "password": "SecurePass123"
}
```

Registration creates a `STAFF` account. The public request does not accept or grant `ADMIN` privileges. A successful response contains the user id, email, and role, but never the password or its hash. Email addresses are normalized to lowercase; duplicate addresses return `409 Conflict`.

Provisioning an `ADMIN` account is intentionally outside the public API and must be performed through a trusted, access-controlled operator process.

**Log in** — `POST /api/auth/login`, Body → raw → JSON:

```json
{
  "email": "staff@example.com",
  "password": "SecurePass123"
}
```

Copy `accessToken` from the response. In Postman, open the request that calls a protected service, select **Authorization** → **Bearer Token**, and paste the token. Alternatively, add the header `Authorization: Bearer <accessToken>`. Login failures return the same `401 Unauthorized` message for an unknown email and an incorrect password.

Tokens are signed with HS256, expire according to `JWT_EXPIRATION_MS`, and carry a `roles` claim (`ADMIN` or `STAFF`). The Gateway, Vehicle, and Maintenance Services validate the same signature and expiry and map those roles to `ROLE_ADMIN` / `ROLE_STAFF`.

## Vehicle Service

The Vehicle Service listens on port `8082`, registers with Eureka as `vehicle-service`, and uses the `fleet_platform_db` PostgreSQL database. All vehicle endpoints require a valid Bearer JWT issued by the Auth Service. Both `ADMIN` and `STAFF` can read vehicles; only `ADMIN` can create, update, or delete them. Registration numbers are trimmed and stored uppercase. Supported vehicle statuses are `ACTIVE`, `INACTIVE`, and `UNDER_MAINTENANCE`.

Run the schema in [`database/schema.sql`](database/schema.sql), then set `DB_USERNAME`, `DB_PASSWORD`, and the same Base64-encoded `JWT_SECRET` used by Auth Service. Start the discovery server separately before starting Vehicle Service:

```powershell
Set-Location .\vehicle-service
mvn spring-boot:run
```

For the second instance on port `8084`, run the same command with the port override:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8084"
```

### Vehicle API

All requests require `Authorization: Bearer <accessToken>`. `POST /api/vehicles` and `PUT /api/vehicles/{id}` accept:

```json
{
  "registrationNumber": "ABC-123",
  "make": "Toyota",
  "model": "Corolla",
  "year": 2022,
  "status": "ACTIVE",
  "assignedDriver": "Casey Driver"
}
```

`assignedDriver` is optional. The API supports create, list, get-by-id, full update, and delete at `/api/vehicles`. It returns `201` for creation, `200` for retrieval/update, `204` for deletion, `400` for invalid input, `401` for missing or invalid authentication, `403` for insufficient role, `404` for an unknown vehicle, and `409` for duplicate registrations.

## Maintenance Service

The Maintenance Service listens on port `8083`, registers with Eureka as `maintenance-service`, and uses `fleet_platform_db`. All endpoints require a valid Bearer JWT issued by Auth Service. Both `ADMIN` and `STAFF` can create, read, and update maintenance records; only `ADMIN` can delete. When creating or updating a record, it validates `vehicleId` through the Eureka-discovered Vehicle Service and forwards the caller's token. A missing vehicle returns `404`; an unavailable Vehicle Service returns `503`.

Run the schema in [`database/schema.sql`](database/schema.sql), then set `DB_USERNAME`, `DB_PASSWORD`, and the same Base64-encoded `JWT_SECRET` used by Auth Service. Start Eureka and Vehicle Service before starting Maintenance Service:

```powershell
Set-Location .\maintenance-service
mvn spring-boot:run
```

Optionally set `VEHICLE_SERVICE_URL` to a custom Vehicle Service URL; the default is `http://vehicle-service`, resolved through Spring Cloud LoadBalancer and Eureka.

### Maintenance API

All requests require `Authorization: Bearer <accessToken>`. `POST /api/maintenance` and `PUT /api/maintenance/{id}` accept:

```json
{
  "vehicleId": 1,
  "serviceType": "PREVENTIVE",
  "description": "Scheduled oil and filter service",
  "scheduledDate": "2026-11-15",
  "completedDate": null,
  "cost": 125.50,
  "status": "SCHEDULED"
}
```

Supported service types: `PREVENTIVE`, `REPAIR`, `INSPECTION`, `OIL_CHANGE`, `TIRE_SERVICE`, `OTHER`. Supported statuses: `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. Completed records require `completedDate`, which cannot precede `scheduledDate`. Create returns `201`; reads and updates return `200`; deletion returns `204`. Invalid data returns `400`, missing authentication `401`, insufficient role `403`, missing records or referenced vehicles `404`, data conflicts `409`, and unavailable Vehicle Service `503`.

Run `mvn clean verify` from an individual service directory. Auth, Vehicle, and Maintenance Service tests use H2 in PostgreSQL compatibility mode and signed JWTs. Maintenance tests mock Vehicle Service for CRUD flow and use a mock HTTP server to test the reference client; live PostgreSQL application connections, Eureka-based service discovery, Vehicle Service communication, and multi-instance runtime integration have not been verified.

## Build

Run `mvn clean verify` from an individual service directory.

See [`docs/architecture.md`](docs/architecture.md), [`docs/requirements.md`](docs/requirements.md), and [`docs/api-test-cases.md`](docs/api-test-cases.md).
