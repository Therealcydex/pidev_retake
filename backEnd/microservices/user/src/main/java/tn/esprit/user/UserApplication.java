package tn.esprit.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point of the User microservice (port 8024).
 *
 * Q: What does @SpringBootApplication actually contain?
 * A: Three annotations in one:
 *      @Configuration      - the class can declare @Bean methods
 *      @EnableAutoConfiguration - Spring Boot configures Tomcat, Hibernate, Jackson,
 *                            the DataSource... from the jars found on the classpath
 *      @ComponentScan      - scans THIS package and its sub-packages for @Component,
 *                            @Service, @Repository, @RestController, @Configuration
 *    That last point explains the package layout: everything lives under
 *    tn.esprit.user.*, otherwise the beans would simply not be discovered.
 *
 * Q: What is @EnableDiscoveryClient for?
 * A: It registers this service with EUREKA (the registry on port 8761) at startup, under
 *    the name `spring.application.name` = "user". Two benefits:
 *      1. The Gateway can route to lb://user without knowing the IP or port.
 *      2. Several instances can register under the same name and be load-balanced.
 *    Without it the Gateway would need a hard-coded http://localhost:8024.
 *
 * Q: What if Eureka is down?
 * A: The service still starts and still answers on 8024; it just retries registration in
 *    the background. But the Gateway can no longer resolve it, so calls through :9090 fail.
 *    Hence the startup order: Eureka -> Gateway -> microservices.
 */
@EnableDiscoveryClient
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        // Bootstraps the whole Spring context and starts the embedded Tomcat server.
        SpringApplication.run(UserApplication.class, args);
    }

}
