# Sample log files

Use these files to try the import independently of the demo scenarios: drag them into **Import Logs**, or

```bash
curl -F files=@samples/monolith-app.log http://localhost:4200/api/imports
```

| File | Format | What it demonstrates |
|------|--------|----------------------|
| `distributed/api-gateway.log` + `distributed/order-service.log` + `distributed/payment-service.jsonl` | text + JSON Lines | **Upload the three files together.** Two requests correlated across services by `correlationId`; one times out after 5 s. There are no span ids, so relations are *inferred* from messages ("Forwarding to order-service", "Calling payment-service") and the waterfall shows observed windows. `payment-service.jsonl` has no `service` field: the service name is taken from the kebab-case file name. The JWT in the order-service log is masked. |
| `spring-boot-tracing.log` | Spring Boot 3 default layout | Micrometer `[traceId-spanId]` sections, two services in one trace, multi-line stack trace with `... common frames omitted`. |
| `monolith-app.log` | Logback default layout | Monolith without tracing (`requestId` used as correlation), NullPointerException with probable origin, an unparseable line, an invalid timestamp kept as raw text, password and cookie masking. |
| `json-array-events.json` | JSON array | Streaming of a JSON array, epoch-millis timestamps, numeric (pino/bunyan) levels, nested `exception` object, grouping by `traceId`. |
