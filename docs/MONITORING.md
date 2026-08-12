# PulseFlow Monitoring & Alert Thresholds

## Exposed endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `/actuator/health` | public | Liveness/readiness for compose/k8s probes |
| `/actuator/info` | public | Build/app info |
| `/actuator/metrics` | ADMIN JWT | Micrometer metrics |
| `/actuator/prometheus` | ADMIN JWT | Prometheus scrape target |

## Key metrics

| Metric | Tags | Meaning |
|--------|------|---------|
| `pulseflow.delivery.success` | `channel` | Successful channel deliveries |
| `pulseflow.delivery.failure` | `channel` | Transient/permanent delivery failures before DLQ |
| `pulseflow.delivery.retry` | `channel` | Retry publish events |
| `pulseflow.delivery.skipped` | `channel` | Non-retryable skips (missing config/template/creds) |
| `pulseflow.delivery.dead_lettered` | `channel` | Exhausted retries / dead-lettered deliveries |
| `rabbitmq.queue.messages` (if available) | queue name | Queue depth including DLQs |

## Suggested alert thresholds (demo + production baseline)

1. **Delivery DLQ growth**
   - Alert when `pulseflow.delivery.dead_lettered` increases by **> 10 in 5 minutes** for any channel.
   - Critical when RabbitMQ queue `pulseflow.delivery.dlq` depth **> 50**.

2. **Failed deliveries**
   - Warning when `pulseflow.delivery.failure` rate **> 5% of success+failure** over 10 minutes.
   - Critical when failure rate **> 20%** over 5 minutes.

3. **Retry storms**
   - Warning when `pulseflow.delivery.retry` **> 100 in 5 minutes**.

4. **Latency (manual / future timer metric)**
   - Target: create → first successful delivery **p95 < 5s** for WEBSOCKET.
   - Target: create → terminal state (DELIVERED or DEAD_LETTERED) **p95 < 15 minutes** (includes retry backoffs).

5. **Event DLQ**
   - Critical when `pulseflow.events.dlq` depth **> 0** for more than 2 minutes in production.

## Correlation IDs

- Ingress HTTP requests set/propagate `X-Correlation-Id` (`CorrelationIdFilter`).
- Delivery jobs carry `correlationId` in `DeliveryJobMessage`.
- Logs include MDC fields `correlationId`, `tenantId`, `notificationId` (JSON via Logstash encoder outside local/dev profiles).

## Dashboard hooks

Point Prometheus/Grafana (or Datadog/New Relic Micrometer bridge) at `/actuator/prometheus` with an ADMIN token or network policy restricting scrape to the monitoring namespace.
