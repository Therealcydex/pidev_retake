package tn.esprit.formation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point of the Formation microservice (port 8084).
 * Registers itself in Eureka under the name "Formation"
 * (spring.application.name in application.properties), which is how the Gateway routes
 * /formations/**, /categories/** and /chapitres/** to it.
 *
 * Q: Why does each microservice have its OWN database?
 * A: This one uses `pidev_formation` and the user service uses `pidev_user`. It is the
 *    "database per service" pattern: each service owns its schema, so one team can change
 *    its tables without breaking the others, and the services stay independently
 *    deployable. The price is that you can no longer JOIN across services - a formation
 *    cannot join the user table - so cross-service data is fetched over HTTP (OpenFeign,
 *    already in the pom) or duplicated.
 *
 * Q: What does spring.jpa.hibernate.ddl-auto=update do?
 * A: Hibernate compares the entities with the schema at startup and ADDS what is missing
 *    (tables, columns). Very convenient in a school project - the database builds itself.
 *    Never in production: it never drops or renames anything, it can miss changes, and it
 *    can lock large tables. The production answer is a migration tool: Flyway or Liquibase.
 *    Other values: `create` (drops and recreates on every start), `create-drop` (also drops
 *    at shutdown, used for tests), `validate` (checks only), `none`.
 */
@EnableDiscoveryClient
@SpringBootApplication
public class FormationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FormationApplication.class, args);
    }

}
