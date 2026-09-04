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

    /**
     * The two failures are told apart on purpose: "your token was refused" is the caller's
     * problem (401), "the USER service could not answer" is ours (502). Collapsing them
     * would report an outage as a login failure, and send the user to re-authenticate for
     * a problem no amount of logging in can fix.
     */
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
