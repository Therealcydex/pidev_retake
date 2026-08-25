package tn.esprit.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * JPA entity mapped to the `app_user` table.
 *
 * Q: Why @Table(name="app_user") instead of letting JPA use the class name?
 * A: `user` is a RESERVED WORD in MySQL (and PostgreSQL). Hibernate would generate
 *    `create table user (...)` and MySQL would reject it with a syntax error.
 *    Renaming the table is the simplest fix; the alternative is escaping with backticks.
 *
 * Q: Why does this class implement Serializable?
 * A: It is a JPA convention (required if the entity is ever detached, cached at the
 *    2nd level, or sent over the network). Not strictly needed here, but expected.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor // Hibernate REQUIRES a no-arg constructor to instantiate entities by reflection
public class User implements Serializable {

    /**
     * Q: What does GenerationType.IDENTITY mean?
     * A: The database generates the id itself (MySQL AUTO_INCREMENT). Hibernate reads
     *    the value back after the INSERT.
     *    Alternatives: AUTO (Hibernate chooses), SEQUENCE (Oracle/Postgres sequence),
     *    TABLE (a dedicated table of counters — portable but slow).
     *    Downside of IDENTITY: Hibernate cannot batch inserts, because it must execute
     *    each INSERT immediately to obtain the generated id.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NOTE (likely question): uniqueness of username/email is enforced only in
    // UserService.signup() via existsByUsername/existsByEmail — there is NO
    // @Column(unique = true) here, so the DB has no UNIQUE constraint.
    // Two concurrent signups with the same username could therefore both succeed.
    // The robust fix is @Column(unique = true) + catching DataIntegrityViolationException.
    private String username;

    /** Stores the BCrypt HASH, never the clear-text password. See UserService.signup(). */
    private String password;

    private String email;

    /**
     * Q: Why EnumType.STRING and not the default EnumType.ORDINAL?
     * A: ORDINAL stores the enum's POSITION (ADMIN=0, TRAINER=1, TRAINEE=2, COMPANY=3).
     *    If someone reorders or inserts a value in the Role enum, every row in the
     *    database silently means something different. STRING stores the name ("TRAINEE"),
     *    which is stable, readable in the DB, and refactor-safe.
     */
    @Enumerated(EnumType.STRING)
    private Role role;
}
