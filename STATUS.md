# SkillUp — Project Status

_Last updated: 2026-08-27_

Handoff notes for continuing work in a new session. See `CLAUDE.md` for the general
architecture; this file records the **current state** and the things that are not
obvious from the code.

---

## 1. What is actually implemented

Only **two** business microservices exist in this repo, plus the three infrastructure
services. `CLAUDE.md` lists Forum / Quiz / Course / Certificate / Event / Job Offer —
**those do not exist here.**

| Service       | Port | Status |
|---------------|------|--------|
| config-server | 8888 | Works. `native` profile, serves `classpath:/config/*.properties` |
| eureka        | 8761 | Works |
| gateway       | 9090 | Works. Routes `/auth`, `/users`, `/formations`, `/categories`, `/chapitres` |
| user          | 8024 | Works. JWT auth, BCrypt, roles ADMIN/TRAINER/TRAINEE/COMPANY |
| Formation     | 8084 | Works. CRUD + stats + PDF export + OpenFeign call to `user` |
| skillup_front | 4200 | Angular 18. Login/signup, formation list/form, categories, stats |

### Databases

**Database-per-service**, not the single shared `pidb` that `CLAUDE.md` claims:

- `pidev_user`
- `pidev_formation`

Both auto-created (`createDatabaseIfNotExist=true`), MySQL on `localhost:3306`,
user `root`, empty password (XAMPP).

---

## 2. Environment gotchas

**`JAVA_HOME` must point at JDK 17 for any terminal Maven build.**
`java` on the PATH is **Temurin JDK 8**, which makes `mvnw` fail with
`Fatal error compiling: invalid flag: --release`.

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"   # Git Bash
```
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # PowerShell
```

This does **not** affect IntelliJ, which uses its own SDK (`jbr-17`).

**Maven works offline** (`./mvnw -o compile`) — all dependencies including
OpenFeign 4.0.4 are already in `~/.m2`. The `maven-dependency-plugin` is *not*
cached, so `dependency:tree` fails offline.

**MySQL must be started manually** from the XAMPP control panel.

---

## 3. How to start

1. MySQL (XAMPP)
2. `eureka` — 8761
3. `config-server` — 8888
4. `gateway` — 9090
5. `user` — 8024 and `Formation` — 8084 (either order)
6. `cd skillup_front && npm start` — 4200

`eureka` and `gateway` are self-contained and can start in any order relative to
config-server. `user` and `Formation` **cannot** — see §4.

In IntelliJ: run each `*Application.java` from the gutter, then use the **Services**
tool window (Alt+8) to manage them. There are no saved run configurations.

From a terminal, one tab per service:
```bash
cd backEnd/config-server && ../mvnw spring-boot:run
cd backEnd/microservices/user && ../../mvnw spring-boot:run
```

Verify: http://localhost:8761 should list `GATEWAY`, `USER`, `FORMATION`.

---

## 4. Recent work (this session)

### Config server: single source of truth

Adopted the pattern from the `Jawher-Bziouech/ESPRIT-PIDEV-4SAE11-2026-SkillUp`
reference repo. Previously every service carried a **full** local
`application.properties` *and* a near-identical copy in `config/` — two files to keep
in sync, with the local one silently winning whenever 8888 was down.

Now the split is by service kind:

| Kind | Services | Config lives in |
|---|---|---|
| Infrastructure | `eureka`, `gateway` | their own `application.properties` only |
| Business | `user`, `Formation` | `config-server/.../config/<name>.properties` only |

- `config/eureka.properties` and `config/gateway.properties` were **deleted**. Eureka
  and the gateway are no longer config clients at all, so the stack is no longer
  config-first and the old "start config-server before eureka" trap is gone.
- `user` and `Formation` now hold only their name, the config import, and actuator —
  port, datasource, JPA, Eureka and `jwt.secret` come from 8888.
- The import is no longer `optional:`. These two **will not boot** without the config
  server; that is the point of the pattern — it removes the drift, at the cost of the
  standalone-boot escape hatch. Failure mode is an explicit startup error, not a
  silent fallback to stale local values.

Deviation from the reference repo, kept on purpose: the import URL stays
`configserver:${CONFIG_SERVER_URL:http://localhost:8888}` rather than a hardcoded
localhost, because `docker-compose.yml` sets `CONFIG_SERVER_URL=http://config-server:8888`.
The Docker `SPRING_DATASOURCE_URL` / `JWT_SECRET` / `EUREKA_...` overrides still work —
OS environment variables outrank config-server data in Spring's property precedence.

The gateway's now-dead `CONFIG_SERVER_URL` env var and its `depends_on: config-server`
were dropped from `docker-compose.yml`.

Their `config/` also carries files for 8 services that do not exist here, `db_elevate_*`
DB names, and a committed Gmail app password — **none of that was copied.**

### Comments removed
All the Q/A "defence prep" Javadoc was stripped from **33 Java files** across
config-server, user and Formation. Two `dto/package-info.java` files were deleted —
they existed only to hold those comments.

Note: the comments were already inside the initial commit, so `git checkout` cannot
restore them. They are gone unless re-written.

### OpenFeign: Formation → user
The navbar username is now retrieved through an inter-service call.

```
Angular navbar → GET /formations/whoami → gateway → Formation
                                                      ↓ Feign (@FeignClient name="USER")
                                                    user → GET /auth/me
```

New files in `backEnd/microservices/Formation/.../formation/`:

| File | Role |
|---|---|
| `client/UserClient.java` | `@FeignClient(name = "USER")`, methods `me()` and `getById(Long)` |
| `client/UserDto.java` | Response shape from the user service |
| `config/FeignConfig.java` | `RequestInterceptor` that forwards the `Authorization` header |
| `service/CurrentUserService.java` | Wraps the call, maps `FeignException` → 401 / 502 |

Modified: `pom.xml` (+`spring-cloud-starter-openfeign`), `FormationApplication`
(+`@EnableFeignClients`), `FormationController` (+`GET /formations/whoami`).

Frontend: `UserInfo` in `models/auth.model.ts`, `whoami()` in
`services/formation.service.ts`, and `navbar.component.ts` now implements `OnInit` —
it seeds the name from `localStorage` then overwrites it with the Feign result.

**Key point:** a Feign call is a new HTTP request and does **not** inherit the
caller's headers. Without `FeignConfig` the user service receives no token,
`Authentication` is null, and `/auth/me` fails.

Test:
```bash
curl -H "Authorization: Bearer <token>" http://localhost:9090/formations/whoami
```

---

## 5. Known issues / TODO

Ranked roughly by how likely a jury is to hit them.

**Formation service**
1. **No security at all** — `SecurityConfig` is `anyRequest().permitAll()`. Port 8084
   is directly reachable, so `curl -X DELETE http://localhost:8084/formations/1`
   works with no token. Fix: copy `JwtAuthFilter` from the user service, or configure
   the `oauth2-resource-server` starter already on the classpath.
2. **Deleting a used `Categorie` returns 500**, not 409 — the FK constraint blows up.
   Decide a policy: forbid (409) / detach / cascade.
3. **N+1 selects** in `listAll()` — `@ManyToOne` is EAGER by default. Fix with
   `JOIN FETCH`.
4. `prix` is `Double` — should be `BigDecimal` for money.
5. `description` / `contenu` are `VARCHAR(255)`. Need `@Lob` or `columnDefinition = "TEXT"`.
6. `delete()` never checks existence first.
7. No `@Transactional` anywhere in the services.

**User service**
1. `/auth/**` is `permitAll()`, so `GET /auth/me` **without** a token gives
   NPE → 500 instead of 401. This also makes Formation report 502 instead of 401 for
   an expired/malformed token. One-line fix in `AuthController.me()`:
   null-check `authentication`.
2. **No input validation** — no `@Valid`, `@NotBlank`, `@Email` anywhere.
3. `update()` nulls out omitted fields and skips the uniqueness check.
4. No `@Column(unique = true)` on `username` / `email` — uniqueness is only enforced
   in the service, and the exists-then-save is not atomic.
5. First-admin chicken-and-egg: signup always assigns `TRAINEE`, and changing a role
   is ADMIN-only. The first admin has to be made directly in SQL.
6. An admin can delete the last remaining admin.

**Config server**
1. ~~Config is duplicated~~ — fixed, see §4.
2. `jwt.secret` and DB credentials are committed in plain text. Config Server supports
   `{cipher}` values with `/encrypt`.
3. No security on port 8888 — one curl reads every secret.
4. `native` + `classpath:` puts config inside the jar, so a change still needs a
   rebuild. `file:./config` or a Git backend would be the real answer.

**Docs**
- `CLAUDE.md` is out of date: it claims a single shared `pidb`, says "Config Server is
  disabled in all services", and lists six microservices that do not exist in this repo.

---

## 6. Git state

Branch `main`, single commit `54d6fd7 Initial commit: SkillUp microservices platform`.
**Everything since is uncommitted.**

Uncommitted work falls into three groups:

1. **Config-server wiring** — `config/*.properties`, the `spring.config.import` lines
   in each service, `pom.xml` changes. Includes two untracked files:
   `config/eureka.properties`, `config/gateway.properties`.
2. **Comment removal** — the 33 stripped Java files and 2 deleted `package-info.java`.
3. **OpenFeign feature** — the new `client/` package, `FeignConfig.java`,
   `CurrentUserService.java`, and the three frontend files.

Worth committing in those three separate chunks rather than one lump.

---

## 7. Verified

- `./mvnw -o compile` from `backEnd/` → all 5 modules, exit 0
- `npx tsc --noEmit -p tsconfig.app.json` from `skillup_front/` → exit 0
- The stack has **not** been run end-to-end since the OpenFeign change. The
  `/formations/whoami` flow compiles but is untested at runtime.
