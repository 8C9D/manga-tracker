# manga-tracker backend

Spring Boot 3.5 service backing the manga-tracker app. MySQL in production, H2
in tests.

## Database schema is managed by Flyway

Migrations live under `src/main/resources/db/migration/`:

- `V1__baseline_schema.sql` — baseline schema matching the JPA entities.
- `V2__hot_path_indexes.sql` — indexes for current query paths.

Schema management by profile:

| Profile      | Flyway     | `ddl-auto`   |
|--------------|------------|--------------|
| default/dev  | enabled    | `validate`   |
| `prod`       | enabled, `baseline-on-migrate=true` | `validate` |
| `test`       | disabled   | `create-drop` |

In dev and prod, Flyway runs migrations on startup and Hibernate only validates
that the entities match the schema. In tests, Hibernate builds the schema on H2
from the entities — the MySQL-specific migration SQL is never executed against H2.

### Adding a new migration

1. Create `src/main/resources/db/migration/V{N}__{description}.sql` where `{N}`
   is the next version number.
2. Write standard MySQL DDL/DML. Each migration runs in its own transaction.
3. Update the JPA entities to match. Migrations and entities must stay in sync,
   or `ddl-auto=validate` will fail on startup.
4. Run `./mvnw test` to keep the suite green.

### Existing Railway production database

The Railway MySQL database pre-dates Flyway — its tables were built by
Hibernate `ddl-auto=update`. Flyway is configured in the `prod` profile with
`baseline-on-migrate=true` and `baseline-version=1`, so on the next deploy:

- Flyway finds the existing tables but no `flyway_schema_history`.
- It creates the history table and records `V1` as already-applied **without
  running it**.
- It then runs `V2` (indexes) against the existing schema.

`baseline-on-migrate` is idempotent after the first run; it has no effect once
the history table exists. It's safe to leave on permanently.

**One-time prod deploy checklist:**

1. Confirm `SPRING_PROFILES_ACTIVE=prod` is set on Railway.
2. Deploy. Flyway baselines automatically and applies `V2`.
3. Verify in logs:
   - `Successfully baselined schema with version: 1`
   - `Migrating schema "..." to version "2 - hot path indexes"`
4. Sanity-check that the app started and `ddl-auto=validate` passed — Hibernate
   logs would fail loudly if the schema didn't match.

If anything looks wrong on the first run, the safe rollback is to drop the
`flyway_schema_history` table and redeploy after fixing the migration — the
data tables are untouched by `V1` (it was skipped).

## Running locally

```bash
./mvnw spring-boot:run
```

Required env vars (see `application.properties` for defaults):

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`
- `DEFAULT_USER_PHONE`
- `APP_AUTH_USERNAME`, `APP_AUTH_PASSWORD` (required in `prod`; have dev defaults otherwise)

## Health endpoints

Three probes are exposed under `/actuator/health` (all public; details and
component names are suppressed via `show-details=never` and
`show-components=never`):

| Endpoint                       | Meaning                                                  | Includes DB? |
|--------------------------------|----------------------------------------------------------|--------------|
| `/actuator/health`             | Aggregate app health                                     | yes          |
| `/actuator/health/liveness`    | JVM/process is alive — has it crashed or wedged?         | no           |
| `/actuator/health/readiness`   | App can serve API traffic — including the database       | yes          |

Liveness intentionally excludes the database: a transient DB outage should not
kill the JVM (a restart would not help). Readiness includes the database via
`management.endpoint.health.group.readiness.include=readinessState,db`, so a
load balancer can drain traffic away from an instance whose DB is unhealthy.

### Railway healthcheck recommendation

Railway's healthcheck restarts the container on repeated failures, so it
behaves as a liveness probe. Point it at **`/actuator/health/liveness`** to
avoid restart loops during MySQL maintenance or transient connectivity issues.
Switch to `/actuator/health/readiness` only if Railway adds true
routing-vs-restart separation.

## Tests

```bash
./mvnw test
```

Tests run against in-memory H2 with `spring.flyway.enabled=false`. No local
MySQL required.
