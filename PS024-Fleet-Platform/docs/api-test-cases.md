# API Test Cases

Auth, Vehicle, and Maintenance Service API behavior is covered by automated tests using H2 in PostgreSQL compatibility mode and signed JWTs. Maintenance tests mock the Vehicle Service reference client. Gateway tests exercise routes against local mock HTTP backends and mock service discovery. These tests do not verify live PostgreSQL, Eureka, actual inter-service communication, or multi-instance runtime integration. Auth cases are covered by Auth Service tests.

| Service | Scenario | Expected result |
|---|---|---|
| Auth | Register with valid user details | Tested: user is created and registration returns the expected user data |
| Auth | Log in with valid credentials | Tested: a signed HS256 JWT with role and expiry is returned |
| Vehicle | Create a vehicle as ADMIN | Tested: `201 Created`, response body, normalized registration and timestamps |
| Vehicle | List or get vehicles as STAFF or ADMIN | Tested: `200 OK` with vehicle data |
| Vehicle | Update a vehicle as ADMIN | Tested: `200 OK` with updated data |
| Vehicle | Delete a vehicle as ADMIN | Tested: `204 No Content` |
| Vehicle | Use a missing vehicle id | Tested: `404 Not Found` |
| Vehicle | Submit blank fields, invalid year, or invalid status | Tested: `400 Bad Request` with field errors |
| Vehicle | Reuse a registration number with different casing | Tested: `409 Conflict` |
| Vehicle | Access without a valid token | Tested: `401 Unauthorized`, including invalid signature and expired token |
| Vehicle | Attempt a write as STAFF | Tested: `403 Forbidden`; STAFF reads remain permitted |
| Maintenance | Create a record as ADMIN or STAFF | Tested: `201 Created`, timestamps, and vehicle reference validation |
| Maintenance | List or retrieve records as ADMIN or STAFF | Tested: `200 OK` with record data |
| Maintenance | Update a record as ADMIN or STAFF | Tested: `200 OK` with updated data |
| Maintenance | Delete a record as ADMIN | Tested: `204 No Content`; STAFF deletion is forbidden |
| Maintenance | Use a missing record or vehicle id | Tested: `404 Not Found` |
| Maintenance | Submit invalid fields, dates, cost, service type, or status | Tested: `400 Bad Request` with field errors |
| Maintenance | Vehicle Service is unavailable during reference validation | Tested: `503 Service Unavailable` |
| Maintenance | Access without valid JWT or use an unauthorized role | Tested: `401 Unauthorized` for missing/invalid/expired token; `403 Forbidden` for denied roles |
| Gateway | POST to auth register/login without a token | Tested: public paths route to `auth-service` |
| Gateway | Route vehicle and maintenance requests | Tested: correct backend receives unchanged path and Authorization header |
| Gateway | Access a protected API without a token | Tested: `401 Unauthorized` |
| Gateway | Send malformed, wrong-signature, or expired JWT | Tested: `401 Unauthorized` |
| Gateway | Backend returns `503 Service Unavailable` | Tested: Gateway preserves the downstream status |

The Auth, Vehicle, and Maintenance Service test suites use H2, not live PostgreSQL. Gateway routing tests use local mock HTTP servers and mock service discovery. Maintenance's vehicle-reference integration is mocked. Live database, Eureka, and end-to-end integration remain unverified.
