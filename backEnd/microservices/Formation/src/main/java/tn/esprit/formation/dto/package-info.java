/**
 * DTOs of the Formation microservice.
 *
 * Q: Why not return the JPA entities directly?
 * A: The reason is even more visible here than in the user service, because these
 *    entities have RELATIONS:
 *
 *    1. INFINITE RECURSION. Formation holds a List<Chapitre>, and each Chapitre holds its
 *       Formation. Serialising a Formation would make Jackson walk
 *       Formation -> Chapitre -> Formation -> ... until a StackOverflowError.
 *       The usual entity-side patches are @JsonIgnore or
 *       @JsonManagedReference/@JsonBackReference; the DTO removes the problem entirely by
 *       flattening the link to a plain id (ChapitreResponse.formationId).
 *
 *    2. LazyInitializationException. Formation.chapitres is LAZY. If Jackson touched it
 *       after the Hibernate session had closed, the serialisation would blow up in the
 *       middle of writing the response - producing a half-written JSON body and a 500
 *       that is painful to debug.
 *
 *    3. CONTROL OF THE PAYLOAD. FormationResponse deliberately does NOT ship the chapters:
 *       the list screen does not need them, so they are not transferred.
 *
 *    4. CONTROLLED INPUT. FormationRequest carries `categorieId`, not a Categorie object.
 *       The client can therefore only POINT AT an existing category, never create or
 *       modify one through a formation payload.
 *
 * Q: Why do the Request DTOs use ids while the Response DTOs use id + label?
 * A: Asymmetry on purpose. On the way IN the server only needs the reference. On the way
 *    OUT the client needs something to DISPLAY, so FormationResponse carries both
 *    categorieId (for the edit form) and categorieNom (for the table), which saves the
 *    front-end a second HTTP call.
 *
 * Q: Why do the Response classes have @AllArgsConstructor but no @NoArgsConstructor,
 *    unlike the user service?
 * A: Because these ones are only ever SERIALISED (server -> client). Jackson needs the
 *    no-arg constructor only to DESERIALISE. It works, but if one of these classes were
 *    ever used as a @RequestBody it would fail at runtime - which is exactly why the user
 *    service keeps both annotations.
 */
package tn.esprit.formation.dto;
