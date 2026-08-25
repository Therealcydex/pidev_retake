package tn.esprit.user.dto;

import lombok.Getter;
import lombok.Setter;

import tn.esprit.user.entity.Role;

/**
 * Body of PUT /users/{id} - the ADMIN-only edit form.
 *
 * Q: Why does THIS DTO expose `role` when SignupRequest does not?
 * A: Because the endpoint using it is protected by @PreAuthorize("hasRole('ADMIN')").
 *    Granting a role is an administrative act; the guard is the annotation on
 *    UserController, not the shape of the DTO.
 *
 * Q: Why is there no password field?
 * A: Changing a password is a separate operation that should require the current one.
 *    Leaving it out means an admin edit can never accidentally overwrite the hash.
 *
 * NOTE (likely question): a null field in the JSON overwrites the stored value with
 * null - see UserService.update().
 */
@Getter
@Setter
public class UpdateUserRequest {
    private String username;
    private String email;
    private Role role;
}
