package tn.esprit.formation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.formation.client.UserClient;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.dto.InscriptionResponse;
import tn.esprit.formation.entity.Formation;
import tn.esprit.formation.entity.Inscription;
import tn.esprit.formation.repository.FormationRepository;
import tn.esprit.formation.repository.InscriptionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InscriptionServiceTest {

    private static final long TRAINEE_ID = 18L;
    private static final long FORMATION_ID = 1L;

    @Mock private InscriptionRepository inscriptionRepository;
    @Mock private FormationRepository formationRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private UserClient userClient;
    @InjectMocks private InscriptionService service;

    private void callerIs(long id, String role) {
        when(currentUserService.currentUser()).thenReturn(new UserDto(id, "u" + id, "u@x.tn", role));
    }

    @Test
    void traineeCanEnrol() {
        callerIs(TRAINEE_ID, "TRAINEE");
        Formation formation = new Formation();
        formation.setId(FORMATION_ID);
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.of(formation));
        when(inscriptionRepository.existsByFormationIdAndUserId(FORMATION_ID, TRAINEE_ID)).thenReturn(false);

        service.enroll(FORMATION_ID);

        ArgumentCaptor<Inscription> saved = ArgumentCaptor.forClass(Inscription.class);
        verify(inscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(TRAINEE_ID);
        assertThat(saved.getValue().getFormation()).isSameAs(formation);
        assertThat(saved.getValue().getDateInscription()).isNotNull();
    }

    @Test
    void trainerMayNotEnrol() {
        callerIs(TRAINEE_ID, "TRAINER");

        assertThatThrownBy(() -> service.enroll(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        verify(inscriptionRepository, never()).save(any());
    }

    @Test
    void enrollingTwiceIsAConflict() {
        callerIs(TRAINEE_ID, "TRAINEE");
        when(formationRepository.findById(FORMATION_ID)).thenReturn(Optional.of(new Formation()));
        when(inscriptionRepository.existsByFormationIdAndUserId(FORMATION_ID, TRAINEE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.enroll(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        verify(inscriptionRepository, never()).save(any());
    }

    @Test
    void unenrollingWhenNotEnrolledIsNotFound() {
        callerIs(TRAINEE_ID, "TRAINEE");
        when(inscriptionRepository.findByFormationIdAndUserId(FORMATION_ID, TRAINEE_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unenroll(FORMATION_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** The ids live here, the names live in the USER service — one Feign call joins them. */
    @Test
    void rosterResolvesNamesThroughTheUserService() {
        Formation formation = new Formation();
        formation.setId(FORMATION_ID);

        Inscription i = new Inscription();
        i.setUserId(TRAINEE_ID);
        i.setFormation(formation);
        i.setDateInscription(Instant.parse("2026-01-02T03:04:05Z"));

        when(inscriptionRepository.findByFormationIdOrderByDateInscriptionAsc(FORMATION_ID))
            .thenReturn(List.of(i));
        when(userClient.getByIds(List.of(TRAINEE_ID)))
            .thenReturn(List.of(new UserDto(TRAINEE_ID, "amine", "amine@esprit.tn", "TRAINEE")));

        List<InscriptionResponse> roster = service.listByFormation(FORMATION_ID);

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).getUsername()).isEqualTo("amine");
        assertThat(roster.get(0).getEmail()).isEqualTo("amine@esprit.tn");
        assertThat(roster.get(0).getUserId()).isEqualTo(TRAINEE_ID);
    }

    /** A user deleted in the other service must not break the roster. */
    @Test
    void rosterSurvivesAUserMissingFromTheUserService() {
        Formation formation = new Formation();
        formation.setId(FORMATION_ID);

        Inscription i = new Inscription();
        i.setUserId(TRAINEE_ID);
        i.setFormation(formation);

        when(inscriptionRepository.findByFormationIdOrderByDateInscriptionAsc(FORMATION_ID))
            .thenReturn(List.of(i));
        when(userClient.getByIds(List.of(TRAINEE_ID))).thenReturn(List.of());

        assertThat(service.listByFormation(FORMATION_ID).get(0).getUsername())
            .isEqualTo("(compte supprimé)");
    }

    @Test
    void emptyRosterSkipsTheUserServiceEntirely() {
        when(inscriptionRepository.findByFormationIdOrderByDateInscriptionAsc(FORMATION_ID))
            .thenReturn(List.of());

        assertThat(service.listByFormation(FORMATION_ID)).isEmpty();
        verify(userClient, never()).getByIds(any());
    }
}
