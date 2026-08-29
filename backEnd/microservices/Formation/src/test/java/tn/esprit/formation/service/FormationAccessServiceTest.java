package tn.esprit.formation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.repository.FormationRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The ownership rule, tested without a Spring context: the caller is a mocked
 * CurrentUserService, so no USER service and no database are needed.
 */
@ExtendWith(MockitoExtension.class)
class FormationAccessServiceTest {

    private static final long OWNER_ID = 7L;
    private static final long OTHER_ID = 8L;
    private static final long FORMATION_ID = 1L;

    @Mock private CurrentUserService currentUserService;
    @Mock private FormationRepository formationRepository;
    @InjectMocks private FormationAccessService access;

    private void callerIs(long id, String role) {
        when(currentUserService.currentUser()).thenReturn(new UserDto(id, "u" + id, "u@x.tn", role));
    }

    private void formationOwnedBy(Long ownerId) {
        Formation f = new Formation();
        f.setId(FORMATION_ID);
        f.setOwnerId(ownerId);
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.of(f));
    }

    @Test
    void adminMayEditAnyFormation() {
        callerIs(OTHER_ID, "ADMIN");
        // No repository lookup needed: an admin short-circuits before loading it.
        assertThatCode(() -> access.requireCanEdit(FORMATION_ID)).doesNotThrowAnyException();
    }

    @Test
    void trainerMayEditOwnFormation() {
        callerIs(OWNER_ID, "TRAINER");
        formationOwnedBy(OWNER_ID);
        assertThatCode(() -> access.requireCanEdit(FORMATION_ID)).doesNotThrowAnyException();
    }

    @Test
    void trainerMayNotEditSomeoneElsesFormation() {
        callerIs(OTHER_ID, "TRAINER");
        formationOwnedBy(OWNER_ID);

        assertThatThrownBy(() -> access.requireCanEdit(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void traineeMayNotEditAnything() {
        callerIs(OWNER_ID, "TRAINEE");
        formationOwnedBy(OWNER_ID);

        assertThatThrownBy(() -> access.requireCanEdit(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class);
    }

    /** Rows created before ownership existed belong to nobody, so only an admin may touch them. */
    @Test
    void unownedFormationIsAdminOnly() {
        callerIs(OWNER_ID, "TRAINER");
        formationOwnedBy(null);

        assertThatThrownBy(() -> access.requireCanEdit(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void missingFormationIsNotFound() {
        callerIs(OWNER_ID, "TRAINER");
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireCanEdit(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void staffCheckAcceptsAdminAndTrainerButNotTrainee() {
        callerIs(OWNER_ID, "ADMIN");
        assertThatCode(access::requireStaff).doesNotThrowAnyException();

        callerIs(OWNER_ID, "TRAINER");
        assertThatCode(access::requireStaff).doesNotThrowAnyException();

        callerIs(OWNER_ID, "TRAINEE");
        assertThatThrownBy(access::requireStaff).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void currentUserIdComesFromTheUserService() {
        callerIs(OWNER_ID, "TRAINER");
        assertThat(access.currentUserId()).isEqualTo(OWNER_ID);
    }
}
