# Config Server — why `user` and `Formation` cannot boot without it

Defence notes. Explains the startup-order requirement, the exact Spring mechanism
behind it, and the difference between the two errors you are likely to hit.

---

## 1. The dependency is arithmetic, not policy

This is the **entire** local config of the `user` service
(`backEnd/microservices/user/src/main/resources/application.properties`):

```properties
spring.application.name=user
spring.cloud.config.enabled=true

spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}

management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
management.metrics.tags.application=${spring.application.name}
```

No `server.port`. No `spring.datasource.url`. No `jwt.secret`. Those exist **only**
here, in `src/main/resources/config/user.properties`:

```properties
server.port=8024
spring.datasource.url=jdbc:mysql://localhost:3306/pidev_user?...
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
jwt.secret=c2tpbGx1cC1qd3Qtc2VjcmV0...
```

The service does not know which port to bind, which database to open, or how to sign
a JWT until it has talked to `:8888`. The startup order isn't a preference — the
information is not in the jar.

---

## 2. The fetch happens before any bean exists

The config fetch is **not** a bean, **not** `@PostConstruct`, and **not** part of the
application context. It runs during *Environment preparation*, before the
`ApplicationContext` is created.

Spring Boot 2.4 introduced the **ConfigData API**; `spring.config.import` is processed
by `ConfigDataEnvironmentPostProcessor`. Real call chain, taken from an actual failure
stack trace in this project:

```
SpringApplication.run
  └─ ConfigDataEnvironment.processAndApply
       └─ processInitial
            └─ ConfigDataEnvironmentContributors.withProcessedImports
                 └─ ConfigDataImporter.resolveAndLoad
                      └─ ConfigDataLoaders.load
                           └─ ConfigServerConfigDataLoader.doLoad   ← HTTP GET to :8888
```

The two classes doing the work are registered in `spring-cloud-config-client-4.0.4.jar`
via `META-INF/spring.factories`:

```
ConfigDataLocationResolver = ConfigServerConfigDataLocationResolver
ConfigDataLoader           = ConfigServerConfigDataLoader
```

`doLoad()` performs a plain `RestTemplate` GET against
`http://localhost:8888/user/default`. On success the client logs two lines worth
pointing at during a live demo:

```
Fetching config from server at : http://localhost:8888
Located environment: name=user, profiles=[default], label=null
```

**The ordering requirement is structural:** those properties must be in the
`Environment` before Spring can construct the `DataSource` bean or start Tomcat,
because those beans read their settings *from* the Environment.

---

## 3. What makes it fatal — the `optional:` prefix

A single token controls the entire behaviour:

| Written as | If `:8888` is unreachable |
|---|---|
| `spring.config.import=optional:configserver:...` | tolerated, boot continues |
| `spring.config.import=configserver:...` ← **what we use** | fatal, boot aborts |

`optional:` is a Spring Boot core marker (`ConfigDataLocation`) meaning "this resource
may legitimately be absent." Without it, `ConfigServerConfigDataLoader.doLoad()` throws:

```
ConfigClientFailFastException: Could not locate PropertySource
and the resource is not optional, failing
    at ConfigServerConfigDataLoader.doLoad(ConfigServerConfigDataLoader.java:201)
Caused by: ResourceAccessException: I/O error on GET request for
    "http://localhost:8888/user/default": Connection refused
```

Verified in this project by stopping the config server and booting `user`.

### Why fail-fast is the design, not a bug

The previous setup used `optional:` **and** kept a full local copy of every property.
Consequences:

- if `:8888` was down the service booted silently on the local values;
- you could not tell which of the two sources was actually live;
- the two copies drifted.

Now there is exactly one source of truth, and the failure is explicit and names the URL
it could not reach. The cost is the lost ability to boot standalone — that is the
deliberate trade.

---

## 4. Two different errors — do not confuse them

### (a) Config server unreachable

```
ConfigClientFailFastException: Could not locate PropertySource and the resource is not optional
```

Runtime failure. The client tried to reach `:8888` and could not.
**Fix:** start the config server first.

### (b) Config client on the classpath with no import declared

```
No spring.config.import property has been defined

Action:
    Add a spring.config.import=configserver: property to your configuration.
```

Raised by `ConfigServerConfigDataMissingEnvironmentPostProcessor`, a plain
`EnvironmentPostProcessor`. It is a **static sanity check** — it never contacts any
server. The rule it enforces: *if `spring-cloud-starter-config` is on the classpath,
an import must be declared*, so a config client is never silently ignored.

Toggle: `spring.cloud.config.import-check.enabled=false`.

**Fix:** either declare the import, or remove `spring-cloud-starter-config` from the pom
if the service is not meant to be a config client.

---

## 5. Which services are config clients

| | `user` / `Formation` | `eureka` / `gateway` |
|---|---|---|
| config client? | yes | no |
| `spring-cloud-starter-config` in pom | yes | removed |
| needs `:8888` to boot | **yes** | no |
| config lives in | `config-server/.../config/<name>.properties` | own `application.properties` |

Infrastructure services are deliberately self-contained. That is why the stack is no
longer "config-first" and why eureka can start before the config server.

---

## 6. Startup order

1. MySQL (XAMPP) — 3306
2. `eureka` — 8761
3. `config-server` — 8888
4. `gateway` — 9090
5. `user` — 8024 and `Formation` — 8084 (either order)
6. `cd skillup_front && npm start` — 4200

Only **one** rule is hard: config-server before `user` and `Formation`.

The rest is soft:

- **eureka before config-server** is only for tidiness — config-server registers with
  eureka, so if eureka is down you get connection-refused warnings for a few seconds,
  then it registers on retry.
- **eureka before gateway** matters only once traffic flows: the gateway routes to
  `lb://USER` and `lb://FORMATION`, which need the registry to resolve. It self-heals
  when eureka appears.

Verify: <http://localhost:8761> should list `GATEWAY`, `USER`, `FORMATION` and
`CONFIG-SERVER`.

---

## 7. Anticipated questions

**What if the config server dies while a service is running?**
Nothing happens. The fetch is startup-only; the values are already in the `Environment`.
It matters again only at the next boot, or on `/actuator/refresh`.

**Isn't this a single point of failure?**
At startup only, and it is the standard trade for a single source of truth. Production
answers: run several config-server instances behind the Eureka registration it already
has, or enable `spring.cloud.config.fail-fast` together with the retry starter so the
client backs off and retries instead of dying.

**Why is the Angular frontend unaffected?**
It talks to the gateway on `:9090`, which is not a config client at all.

**Why is the import written with `${CONFIG_SERVER_URL:http://localhost:8888}`?**
So Docker can override the host. `docker-compose.yml` sets
`CONFIG_SERVER_URL=http://config-server:8888`; the default after the colon is used for
local IDE runs.
