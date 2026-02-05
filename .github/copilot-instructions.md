# Project Guidelines

## Code Style
- Backend is Spring Boot + WebFlux; prefer reactive flows and avoid blocking in request paths.
- Frontend is React + Vite with page components in [frontend/src/pages](frontend/src/pages).

## Architecture
- Gateway core is Spring Cloud Gateway with rate limiting and anti-bot filters under [backend/src/main/java/com/example/gateway/filter](backend/src/main/java/com/example/gateway/filter).
- Rate limiting uses a Postgres-backed limiter in [backend/src/main/java/com/example/gateway/ratelimit](backend/src/main/java/com/example/gateway/ratelimit) and config in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml).
- Admin/config/analytics controllers are in [backend/src/main/java/com/example/gateway/controller](backend/src/main/java/com/example/gateway/controller).

## Build and Test
- Backend build: `mvn clean package -DskipTests` (used by [backend/Dockerfile](backend/Dockerfile)).
- Frontend dev: `npm run dev` and build: `npm run build` (see [frontend/package.json](frontend/package.json)).
- Docker compose: `docker compose up --build` (see [docker-compose.yml](docker-compose.yml)).

## Project Conventions
- API requests from the frontend are centralized in [frontend/src/api.js](frontend/src/api.js).
- Token-based anti-bot flow uses `/api/tokens/form` and helpers in [frontend/src/utils/formProtection.js](frontend/src/utils/formProtection.js).

## Integration Points
- Nginx in the frontend container proxies `/api/` to the backend; see [frontend/nginx.conf](frontend/nginx.conf).
- Gateway routes include a default `/httpbin/**` proxy target in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml).

## Security
- `trust-x-forwarded-for` is a sensitive setting in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml).
- CORS is configured at the controller level; see [backend/src/main/java/com/example/gateway/controller](backend/src/main/java/com/example/gateway/controller).
- Postgres credentials are defined in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) and [docker-compose.yml](docker-compose.yml).
