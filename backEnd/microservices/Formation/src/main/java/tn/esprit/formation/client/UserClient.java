package tn.esprit.formation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "USER")
public interface UserClient {

    @GetMapping("/auth/me")
    UserDto me();
}
