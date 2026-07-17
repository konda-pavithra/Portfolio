# Portfolio — microservices

Nifty 50 stock portfolio tracker, decomposed from a single Spring Boot monolith into nine
Maven modules: a shared library (`common`) plus eight independently deployable services.

## Services

| Service | Port | Owns | Responsibility |
|---|---|---|---|
| `eureka-server` | 8761 | — | Service discovery |
| `config-server` | 8888 | — | Centralized config (native backend, `config-server/src/main/resources/config-repo`) |
| `api-gateway` | 8080 | — | Single public entry point, routing + CORS |
| `user-service` | 8081 | `user_service.users` | Registration, login, Google OAuth, JWT issuance |
| `stock-service` | 8082 | — (in-memory) | Polls Yahoo Finance, publishes to Kafka `stock.prices` |
| `portfolio-service` | 8083 | `portfolio_service.portfolio` | Holdings CRUD, Excel upload, valuation, SSE stream |
| `threshold-service` | 8084 | `threshold_service.stock_thresholds` | Price-alert thresholds, breach scheduler, RabbitMQ producer |
| `notification-service` | 8085 | — | RabbitMQ consumer → email |

All browser traffic goes through `api-gateway` (`:8080/api/**`). Services talk to each other
via OpenFeign + Eureka client-side load balancing on internal-only `/internal/**` endpoints
(never routed through the gateway). Stock prices flow one-way over Kafka: `stock-service`
publishes once, `portfolio-service` and `threshold-service` each consume independently into
their own local cache.

## Build

```bash
./mvnw -q clean install -DskipTests   # full reactor
./mvnw -q -pl <module> test           # single module's tests, e.g. -pl portfolio-service
```

## Run everything with Docker Compose

```bash
docker compose up --build
```

This starts Postgres (with `user_service`, `portfolio_service`, `threshold_service` schemas
pre-created), Kafka (KRaft, no ZooKeeper), RabbitMQ, and all eight services. First boot can
take a few minutes while every service's Docker image compiles from source.

Check everything registered: http://localhost:8761
Check a service's resolved config: http://localhost:8888/user-service/default

Optional environment variables (defaults shown) — set before `docker compose up` or export in your shell:

```bash
GOOGLE_CLIENT_ID=...      # Google OAuth2 client ID for /api/users/google
SMTP_USERNAME=...         # From address for threshold-breach alert emails
SMTP_PASSWORD=...         # SMTP app password
```

## Smoke test (through the gateway, `:8080`)

```bash
# Register + login
curl -X POST localhost:8080/api/users/register -H 'Content-Type: application/json' \
  -d '{"username":"john_doe","email":"john@example.com","password":"Secret@123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/users/login -H 'Content-Type: application/json' \
  -d '{"username":"john_doe","password":"Secret@123"}' | jq -r .token)

# Live quotes (populates after stock-service's first ~30s poll cycle)
curl localhost:8080/api/stocks/quotes -H "Authorization: Bearer $TOKEN"

# Add a holding, check valuation
curl -X POST localhost:8080/api/portfolio/add -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"symbol":"RELIANCE","quantity":10,"buyingPrice":2450.00}'
curl localhost:8080/api/portfolio/valuation -H "Authorization: Bearer $TOKEN"

# Set a price alert — breach evaluation runs every 60s in threshold-service
curl -X PUT localhost:8080/api/thresholds/RELIANCE -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"upperThresholdPercent":1.0,"lowerThresholdPercent":1.0}'

# Live SSE portfolio stream
curl -N "localhost:8080/api/portfolio/stream?token=$TOKEN"
```

A threshold breach flows: `threshold-service` scheduler → RabbitMQ `stock.alerts.exchange` →
`notification-service` → SMTP. Watch `docker compose logs -f threshold-service notification-service`
to see it happen.

## Swagger UI

Each business service exposes its own OpenAPI docs directly (not aggregated through the
gateway): `http://localhost:<port>/swagger-ui.html`, e.g. `http://localhost:8083/swagger-ui.html`
for portfolio-service.
