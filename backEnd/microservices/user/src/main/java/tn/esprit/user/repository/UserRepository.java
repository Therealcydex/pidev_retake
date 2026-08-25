package tn.esprit.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tn.esprit.user.entity.User;

/**
 * Data-access layer.
 *
 * Q: Where is the implementation of this interface? I only see method signatures.
 * A: There is none written by hand. At startup Spring Data JPA creates a dynamic PROXY
 *    that implements the interface, and DERIVES the SQL from the METHOD NAME
 *    ("query derivation"): findByUsername -> SELECT * FROM app_user WHERE username = ?
 *    The name must match the ENTITY FIELD names exactly, otherwise the context fails
 *    to start (which is good — the error appears at boot, not at runtime).
 *
 * Q: What does extending JpaRepository<User, Long> give us?
 * A: The generics are <EntityType, PrimaryKeyType>. We inherit ~20 ready methods:
 *    save, saveAll, findById, findAll, existsById, deleteById, count, plus paging
 *    and sorting. That is why UserService can call findAll()/findById() without us
 *    declaring them here.
 *
 * Q: Why Optional<User> rather than User?
 * A: It forces the caller to handle the "not found" case. UserService uses
 *    .orElseThrow(...) to convert absence into a proper HTTP status instead of
 *    risking a NullPointerException.
 *
 * Q: Is @Repository necessary?
 * A: Not strictly — Spring Data detects the interface anyway. It documents the role
 *    and enables translation of JDBC exceptions into Spring's DataAccessException.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Used by login and by /auth/me (the JWT subject is the username).
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    // exists... generates SELECT COUNT(*) — cheaper than loading the whole entity
    // just to test presence. Used by signup() for the duplicate checks.
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
