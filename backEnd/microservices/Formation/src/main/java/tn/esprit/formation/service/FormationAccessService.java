package tn.esprit.formation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.repository.FormationRepository;

import java.util.List;

/**
 * Every "may this caller touch this formation?" rule, in one place.
 *
 * Formation has no JWT filter of its own — SecurityConfig is permitAll — so there is no
 * Spring Security principal for @PreAuthorize to read. The caller is resolved instead
 * through the USER service over Feign, with the incoming token relayed by FeignConfig.
 */
@Service
@RequiredArgsConstructor
public class FormationAccessService {

    private static final List<String> STAFF_ROLES = List.of("ADMIN", "TRAINER");

    private final CurrentUserService currentUserService;
    private final FormationRepository formationRepository;

    /**
     * Shared reference data — categories, and the admin-only reports — belongs to the
     * whole catalogue rather than to one trainer, so only an admin may change it.
     */
    public void requireAdmin() {
        UserDto user = currentUserService.currentUser();
        if (!"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Réservé à l'administrateur");
        }
    }

    /** Creating a formation needs no owner yet — any staff member may. */
    public void requireStaff() {
        UserDto user = currentUserService.currentUser();
        if (user.getRole() == null || !STAFF_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Réservé à un admin ou un formateur");
        }
    }

    /**
     * Editing, deleting, changing the image, and reading the roster all share one rule:
     * an admin may touch anything, a trainer only what they created.
     */
    public void requireCanEdit(Long formationId) {
        UserDto user = currentUserService.currentUser();
        if ("ADMIN".equals(user.getRole())) {
            return;
        }

        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        boolean owns = "TRAINER".equals(user.getRole())
            && formation.getOwnerId() != null
            && formation.getOwnerId().equals(user.getId());

        if (!owns) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Vous ne pouvez gérer que les formations que vous avez créées");
        }
    }

    /** The id of whoever is calling, so create() can stamp the owner. */
    public Long currentUserId() {
        return currentUserService.currentUser().getId();
    }
}
