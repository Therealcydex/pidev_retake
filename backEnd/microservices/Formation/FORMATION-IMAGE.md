# Formation image — defence notes

How an admin or a trainer attaches an illustration to a formation, and why each piece is
built the way it is.

---

## 0. The short version — what to actually say

> "An admin or a trainer uploads an image on a formation. It's stored in the database in
> its own table and shown to trainees in the catalogue. Only PNG, JPG and WebP are
> accepted, up to 5 Mo, and the server checks the role of whoever uploads."

That is the whole feature. Everything below is there in case a juror digs.

---

## 1. Request flow

```
Browser                Gateway :9090          Formation :8084            MySQL
  |                        |                        |                      |
  | POST /formations/1/image                        |                      |
  |  multipart/form-data   |                        |                      |
  |----------------------->|                        |                      |
  |                        | lb://FORMATION (Eureka)|                      |
  |                        |----------------------->|                      |
  |                        |                        | 1. size check (Spring multipart)
  |                        |                        | 2. role check (via USER service)
  |                        |                        | 3. extension + type + magic bytes
  |                        |                        | 4. save                 |
  |                        |                        |--------------------->|
  |                        |    200 + FormationResponse                    |
  |<-----------------------|<-----------------------|                      |
  |                                                                        |
  | GET /formations/1/image  -> the image, with an ETag                    |
```

## 2. Why an image and not the slide deck?

This project previously stored a `.pptx` and rendered its first slide server-side with
Apache POI. It worked, but for "show a picture of the course" it cost a 12 MB dependency,
headless AWT rendering, a 50 MB upload limit that had to be raised across four layers, and
a fallback path for decks POI could not draw — POI's renderer is approximate, so an
unusual font or a SmartArt diagram could come out wrong.

Uploading the image directly removes all of that, and has one property the render path
could never offer: **what the trainee sees is exactly what was uploaded**, with no
rendering approximation in between.

The honest trade-off, if a juror pushes: the trainee no longer gets the deck itself. That
was acceptable because the goal was always to *show* what the formation offers, not to
distribute the source file.

## 3. The code

### 3.1 `entity/FormationImage.java` — a separate table on purpose

```java
@Entity @Table(name = "formation_images")
public class FormationImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String filename;
    private String contentType;
    private Long sizeBytes;

    @Lob @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @OneToOne @JoinColumn(name = "formation_id", unique = true)
    private Formation formation;
}
```

**Why a separate table and not a column on `formations`?** `GET /formations` returns every
formation. If the bytes lived on `Formation`, every `findAll()` would pull every image
into the JVM heap just to render a list of titles. In its own table, the list query never
touches it.

Note the relation is **unidirectional**: `Formation` holds no reference back to
`FormationImage`. That is what structurally guarantees the blob can never be loaded as a
side effect of loading a formation.

**Why `MEDIUMBLOB`?** Hibernate maps `byte[]` to MySQL `BLOB` by default, which caps at
**64 KB** — far too small. `MEDIUMBLOB` allows 16 MB, comfortably above the 5 MB cap while
signalling the intended scale.

**Why `unique = true`?** One image per formation, enforced by the database rather than only
by application logic.

### 3.2 `repository/FormationImageRepository.java` — avoiding N+1

The list needs to know *whether* a formation has an image and *what it is called* — never
its bytes. Two projection queries do exactly that:

```java
@Query("select i.filename from FormationImage i where i.formation.id = :formationId")
Optional<String> findFilenameByFormationId(Long formationId);

@Query("select i.formation.id, i.filename from FormationImage i")
List<Object[]> findAllFormationIdAndFilename();
```

A JPQL `select i.filename` reads **only that column**. `FormationService.listAll()` calls
the second one **once**, builds a `Map<Long, String>`, and looks each formation up in
memory — **one image query no matter how many formations exist**. The naive version, a
repository call inside the `map()`, is the classic **N+1 problem** (1 + N queries). Say
this before they ask; it is a standard jury question.

Verified with `spring.jpa.show-sql=true`: `GET /formations` issues
`select f1_0.formation_id, f1_0.filename from formation_images f1_0` — the blob column is
absent — plus the `formations` select itself.

**Be precise if pushed:** there is a *third* query, for `categories`. `Formation.categorie`
is a plain `@ManyToOne`, which defaults to `FetchType.EAGER`, so Hibernate resolves
categories separately. That is pre-existing behaviour unrelated to the image feature, and
the honest fix is `@ManyToOne(fetch = FetchType.LAZY)` plus a `join fetch` on the list
query. Knowing this is better than claiming a flat "2 queries" and being corrected.

### 3.3 `service/FormationImageService.java` — three layers of validation

`store(...)` rejects, in order:

1. Missing formation → **404**; empty file → **400**.
2. Extension not in `.png` / `.jpg` / `.jpeg` / `.webp` → **415**.
3. Declared `Content-Type` not starting with `image/` → **415**.
4. **Magic bytes** wrong → **415**.

The fourth check is the one worth explaining. The extension and the content type are both
supplied by the client, so both can be lied about — renaming `virus.exe` to `photo.png`
defeats them. The magic bytes are the first bytes of the file itself:

```java
// PNG: 89 50 4E 47   JPEG: FF D8 FF   WebP: "RIFF" .... "WEBP"
```

A client cannot fake those without actually sending an image. It is three cheap prefix
comparisons, and it closes the hole.

An existing row is reused rather than inserted
(`findByFormationId(...).orElseGet(FormationImage::new)`), so re-uploading **replaces**
the image instead of accumulating orphan rows.

### 3.4 The role check — why not `@PreAuthorize`

Only **ADMIN** and **TRAINER** may upload or delete; trainees have view-only access.

`@PreAuthorize` cannot express this here. Formation's `SecurityConfig` is
`anyRequest().permitAll()` with no JWT filter, so there is no Spring Security principal for
it to evaluate — the annotation would have nothing to check. Instead the caller is resolved
through the mechanism the service already has:

```java
public void requireUploaderRole() {
    UserDto user = currentUserService.currentUser();   // 401 if the token is missing/bad
    if (user.getRole() == null || !UPLOADER_ROLES.contains(user.getRole())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "...");
    }
}
```

`CurrentUserService` calls `/auth/me` on the USER service through **OpenFeign**, and
`config/FeignConfig.java` installs a `RequestInterceptor` that relays the caller's
`Authorization` header onto that internal call. So Formation never parses the JWT itself —
it asks the service that owns identity. That is a legitimate microservices pattern, and it
means the token secret lives in exactly one service.

This is a real improvement on the previous state, where upload and delete were guarded only
by an Angular route guard that anyone could bypass with `curl`.

### 3.5 `controller/FormationController.java`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/formations/{id}/image` | ADMIN or TRAINER only |
| `GET` | `/formations/{id}/image` | the image, `Content-Disposition: inline` |
| `DELETE` | `/formations/{id}/image` | ADMIN or TRAINER only |

The `GET` sets caching headers:

```java
.eTag("\"" + image.getId() + "-" + image.getSizeBytes() + "\"")
.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
```

The `ETag` lets the browser revalidate and receive **304 Not Modified** instead of
re-downloading; `Cache-Control` avoids even asking for an hour. `cachePrivate()` means the
browser may cache it but shared proxies may not. Without these, the catalogue would
re-fetch every image on every navigation.

The response `Content-Type` comes from the stored `contentType` column, so a JPEG is served
as `image/jpeg` and a WebP as `image/webp`.

### 3.6 `controller/UploadExceptionHandler.java` — why advice, not a handler method

A subtle point worth raising yourself. `MaxUploadSizeExceededException` is thrown by the
**multipart resolver while the request is still being parsed** — *before* Spring has
resolved which controller method should handle it. An `@ExceptionHandler` written inside
`FormationController` is therefore never consulted: there is no "current controller" yet.
Only a global `@RestControllerAdvice` catches it. We hit exactly this during development:
with the handler on the controller, an oversized upload reached the browser as an opaque
**500**; moving it to advice produced a clean **413** with a readable French message.

### 3.7 Front end

```typescript
uploadImage(id: number, file: File): Observable<Formation> {
  const form = new FormData();
  form.append('file', file);
  return this.http.post<Formation>(this.api + '/' + id + '/image', form);
}
```

**`Content-Type` is deliberately not set.** The browser must generate
`multipart/form-data; boundary=----WebKitFormBoundary…` itself — the boundary token is
random per request, and setting the header by hand without it makes the body unparsable.

Two client-side guards (extension and 5 MB) run before the request is sent, so an obvious
mistake costs nothing. They are **convenience, not security** — both are re-checked on the
server, because anything in a browser can be bypassed.

A `previewVersion` counter is appended to the image URL and incremented after each upload;
without it the caching headers from §3.5 would keep showing the *old* image after a
replace.

**Where each role uploads.** The formation form is behind `adminGuard`, and trainers must
not gain formation editing — so the trainer's upload control lives in the **formations list
modal** instead, shown when the logged-in role is ADMIN or TRAINER. Admins can use either.

## 4. The size limit — a chain of three layers

A 5 MB upload succeeds only if every layer allows it, and the ordering matters:

| # | Layer | Setting | Value |
|---|---|---|---|
| 1 | Browser | `MAX_IMAGE_BYTES` | 5 MB |
| 2 | Gateway | `spring.codec.max-in-memory-size` | **8 MB** |
| 3 | Formation | `spring.servlet.multipart.max-file-size` | **5 MB** |

Layer 2 is deliberately set **above** layer 3. Spring Cloud Gateway buffers the request
body before forwarding, and its default limit is only **256 KB** — low enough to reject any
real upload. Giving the gateway more headroom than the service means the size decision
belongs to Formation, which answers with a proper 413; the gateway never becomes the
bottleneck.

`server.tomcat.max-swallow-size=-1` is also set: it tells Tomcat to keep reading and discard
the body of a request it has already rejected, instead of resetting the connection. Without
it the client sees a connection error rather than the 413 body.

Note that limit 3 lives in `config-server/src/main/resources/config/Formation.properties`,
not in the microservice — the size policy is **centralised configuration**, changed in one
place for every instance. (Which also means config-server must be restarted for a change to
take effect; it serves the compiled copy from its classpath.)

MySQL's `max_allowed_packet` was raised to 128 MB for the previous `.pptx` feature. A 5 MB
image is far below even the 1 MB default's successor, so it is no longer a constraint —
but it is worth knowing it exists, because a blob larger than that value is rejected by the
database no matter what the Java-side limits say.

**Measured behaviour** (verified against a running instance):

| Case | Result |
|---|---|
| no token | **401** |
| TRAINEE token | **403** |
| TRAINER token, valid PNG | **200** |
| ADMIN token, valid PNG | **200** |
| download, then `cmp` against the original | byte-identical |
| second `GET` with `If-None-Match` | **304** |
| text file renamed `.png`, declared `image/png` | **415** (magic bytes) |
| `.pptx` | **415** |
| valid 7.7 MB PNG | **413**, `"Le fichier dépasse la taille maximale autorisée (5 Mo)."` |

## 5. Honest limitations

- **Blobs in a relational database** are convenient for a single-machine project — one
  backup covers everything, no orphan files, and the image commits in the same transaction
  as its row. They inflate the database, and at scale the answer is object storage (S3 or
  MinIO) with only a URL in the table.
- **The role check costs a network call.** Every upload asks the USER service who the caller
  is. That is one extra hop, and it makes Formation depend on USER being up. A JWT filter
  validating the signature locally would avoid it, at the cost of sharing the secret.
- **One image per formation.** A gallery would be a `@OneToMany` and a display order
  column — scoped out, not overlooked.
- **No image re-encoding.** A 5 MB photo is served to trainees at 5 MB. Generating a
  downscaled thumbnail at upload would make the catalogue much lighter.

## 6. Likely jury questions

**"Why not just upload the PowerPoint?"** — see §2.

**"Why store the image in the database rather than on disk?"**
One transactional unit and one backup. On disk I would have to keep the filesystem and the
database in sync by hand and deal with orphan files. The trade-off is database size, and I
would move to object storage if volume grew.

**"How do you stop someone uploading an executable renamed to .png?"**
Three checks: extension, declared content type, and the file's magic bytes. The first two
come from the client and can be faked; the magic bytes cannot be, without sending a real
image.

**"Why doesn't listing formations get slow?"**
The bytes live in a separate table, the relation is unidirectional so a formation never
loads its image, and the list uses a projection returning only `(formationId, filename)` in
a single query — no N+1 on images however many formations there are. (If they press:
categories are still fetched eagerly, which is a separate pre-existing issue — see §3.2.)

**"Who can upload, and how is that enforced?"**
ADMIN and TRAINER. Enforced on the server by resolving the caller through the USER service
over Feign — not just hidden in the Angular UI. A trainee gets 403, no token gets 401.

**"Why PNG, JPG and WebP?"**
They cover every realistic source: exports, photos and screenshots. Anything else is
refused rather than stored as an unknown type.

**"How would you improve it?"**
Generate a downscaled thumbnail for the catalogue, move the bytes to object storage, and
allow several images per formation.

## 7. Demo script

1. Log in as **admin**, open a formation, use the "Image de la formation" card. The image
   appears immediately, without a page refresh.
2. Log in as a **trainer**: the formations list shows the image button; upload from the
   modal. Confirm `/formations/:id/edit` is still refused (admin-only).
3. Log in as a **trainee**: the image is visible, the file input is not.
4. Try a `.pptx` or a renamed text file → clear 415, no stack trace.
5. Try an image over 5 Mo → instant refusal with the French message.
6. Open DevTools → Network on a second visit and point out the **304** on the image
   request: the caching headers are doing their job.
