package tn.esprit.formation.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.client.UserClient;
import tn.esprit.formation.client.UserDto;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserClient userClient;

    public UserDto currentUser() {
        try {
            return userClient.me();
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service unreachable");
        }
    }
}
