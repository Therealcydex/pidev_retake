package tn.esprit.formation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Calls the USER microservice. `name = "USER"` is the Eureka service id, so Feign
 * resolves the address through discovery rather than a hard-coded host, and
 * FeignConfig relays the caller's Authorization header onto the request.
 */
@FeignClient(name = "USER")
public interface UserClient {

    @GetMapping("/auth/me")
    UserDto me();

    /** One call for many ids, rather than one call per enrolled trainee. */
    @GetMapping("/users/by-ids")
    List<UserDto> getByIds(@RequestParam("ids") List<Long> ids);
}
