/**
 * DTOs - Data Transfer Objects: the classes that shape what enters and leaves the API.
 *
 * Q: Why not simply expose the User entity in the controllers?
 * A: Four reasons, and this is one of the most frequent jury questions:
 *
 *   1. SECURITY. User carries the BCrypt password hash. Jackson serialises every getter,
 *      so returning the entity would publish the hash in the JSON. UserResponse simply
 *      has no password field, so it cannot leak.
 *
 *   2. CONTRACT STABILITY. The entity mirrors the database. Renaming a column or adding
 *      a field would silently change the REST contract and break the Angular client.
 *      The DTO decouples the two: the schema can evolve without touching the API.
 *
 *   3. INPUT CONTROL. SignupRequest deliberately has NO role field, so a client cannot
 *      post {"role":"ADMIN"} and promote itself - a privilege-escalation attack known as
 *      mass assignment / over-posting. The service is what decides the role (TRAINEE).
 *
 *   4. SERIALISATION SAFETY. Once entities gain bidirectional relations
 *      (Formation <-> Chapitre), Jackson follows the cycle and throws a
 *      StackOverflowError. DTOs cut the cycle. It also avoids LazyInitializationException
 *      when Hibernate tries to load a lazy collection after the session is closed.
 *
 * Q: The DTOs look like near-copies of the entity - is that not duplication?
 * A: Yes, and it is accepted on purpose: the duplication is what buys the decoupling.
 *    On a larger project MapStruct generates the entity <-> DTO mapping automatically;
 *    here the mapping is written by hand in UserService, which is fine at this size.
 *
 * Q: Why do the response DTOs need @NoArgsConstructor AND @AllArgsConstructor?
 * A: @AllArgsConstructor is what UserService uses to build them in one line.
 *    @NoArgsConstructor is required by Jackson, which instantiates an empty object and
 *    then calls the setters when DESERIALISING. Declaring only the all-args constructor
 *    removes the implicit default one and breaks deserialisation - the reason for the
 *    "IN ORDER !!" note left in AuthResponse.
 */
package tn.esprit.user.dto;
