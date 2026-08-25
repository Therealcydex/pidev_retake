package tn.esprit.user.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.user.dto.UpdateUserRequest;
import tn.esprit.user.dto.UserResponse;
import tn.esprit.user.service.UserService;

import java.util.List;

/**
 * Administration CRUD over users. This is the classic "CRUD" the jury looks for.
 *
 * Q: Why is @PreAuthorize on the CLASS and not on each method?
 * A: It applies to every method at once - no risk of forgetting one when a new endpoint
 *    is added. Put it on a method only when that method needs a DIFFERENT rule (a method
 *    annotation overrides the class one).
 *
 * Q: hasRole('ADMIN') or hasAuthority('ADMIN')?
 * A: hasRole adds the "ROLE_" prefix automatically and therefore looks for "ROLE_ADMIN".
 *    That matches what JwtAuthFilter builds. hasAuthority compares the string as-is and
 *    would need 'ROLE_ADMIN' spelled out.
 *
 * Q: What does a non-admin get?
 * A: 403 FORBIDDEN (authenticated but not allowed), whereas no token at all gives 401.
 *
 * Reminder: this whole mechanism only works because SecurityConfig is annotated with
 * @EnableMethodSecurity. Without it, @PreAuthorize is ignored and this becomes an open
 * admin API.
 *
 * REST mapping of the endpoints below:
 *   GET    /users       -> read the collection    200
 *   GET    /users/{id}  -> read one item          200 / 404
 *   PUT    /users/{id}  -> replace one item       200 / 404
 *   DELETE /users/{id}  -> delete one item        204 / 404
 * Creation is absent on purpose: it happens through POST /auth/signup.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        return ResponseEntity.ok(userService.listAll());
    }

    /**
     * Q: What does @PathVariable do?
     * A: It binds the {id} segment of the URL to the method parameter and converts the
     *    String to Long. A non-numeric id produces a 400 automatically.
     *    Do not confuse it with @RequestParam, which reads the query string (?id=5).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /**
     * Q: Why PUT and not PATCH?
     * A: PUT means "replace the resource with this representation" - every field is
     *    expected in the body. PATCH means "apply this partial modification". The service
     *    overwrites all three fields unconditionally, so PUT is the honest verb here
     *    (see the null-field warning in UserService.update()).
     *
     * Q: Why is PUT said to be idempotent?
     * A: Sending the same PUT ten times leaves the resource in the same state as sending
     *    it once. POST is not idempotent - ten signups create ten accounts.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * Q: Why 204 No Content instead of 200?
     * A: The deletion succeeded and there is nothing meaningful left to return.
     *    ResponseEntity<Void> + noContent() expresses exactly that.
     *
     * NOTE (likely question): nothing prevents an admin from deleting their own account,
     * and nothing stops the deletion of the LAST admin - after which no one can
     * administer the system again. A guard on that is a good improvement to mention.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
