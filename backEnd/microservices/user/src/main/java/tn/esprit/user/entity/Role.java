package tn.esprit.user.entity;

/**
 * The four roles of the platform.
 *
 * Q: Why an enum instead of a String column or a separate Role table?
 * A: An enum gives compile-time safety (impossible to store a typo like "TRAINEEE")
 *    and needs no join. The trade-off: adding a role requires recompiling and
 *    redeploying. A `roles` table would allow adding roles at runtime — that is the
 *    right design for a real app with dynamic permissions, overkill here.
 *
 * Q: Where is the role used for authorization?
 * A: JwtService puts it in the token as the "role" claim -> JwtAuthFilter converts it
 *    into a Spring Security authority "ROLE_" + name -> UserController checks it with
 *    @PreAuthorize("hasRole('ADMIN')").
 *
 * NOTE (likely question): signup() always assigns TRAINEE, and only an ADMIN can change
 * a role via PUT /users/{id}. So the FIRST admin cannot be created through the API —
 * it must be inserted directly in the database (or seeded at startup). Be ready for
 * "how do you create your first administrator?".
 */
public enum Role {
    ADMIN,
    TRAINER,
    TRAINEE,
    COMPANY
}
