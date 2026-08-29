package tn.esprit.formation.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InscriptionService {

    private static final List<String> STAFF_ROLES = List.of("ADMIN", "TRAINER");

    private final InscriptionRepository inscriptionRepository;
    private final FormationRepository formationRepository;
    private final CurrentUserService currentUserService;
    private final UserClient userClient;

    /** Enrols the caller. Enrolling is a trainer action. */
    @Transactional
    public void enroll(Long formationId) {
        UserDto user = currentUserService.currentUser();
        if (!"TRAINER".equals(user.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Seuls les formateurs peuvent s'inscrire à une formation");
        }

        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation not found"));

        if (inscriptionRepository.existsByFormationIdAndUserId(formationId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Déjà inscrit à cette formation");
        }

        Inscription inscription = new Inscription();
        inscription.setFormation(formation);
        inscription.setUserId(user.getId());
        inscription.setDateInscription(Instant.now());
        inscriptionRepository.save(inscription);
    }

    @Transactional
    public void unenroll(Long formationId) {
        UserDto user = currentUserService.currentUser();
        Inscription inscription = inscriptionRepository
            .findByFormationIdAndUserId(formationId, user.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vous n'êtes pas inscrit à cette formation"));
        inscriptionRepository.delete(inscription);
    }

    /** Formation ids the caller is enrolled in — drives "Mes formations". */
    @Transactional(readOnly = true)
    public List<Long> myFormationIds() {
        UserDto user = currentUserService.currentUser();
        return inscriptionRepository.findByUserId(user.getId()).stream()
            .map(i -> i.getFormation().getId())
            .toList();
    }

    /**
     * Who is enrolled in a formation, for admins and trainers.
     *
     * The ids live here; the names live in the USER service. They are resolved in a
     * single Feign call rather than one per row.
     */
    @Transactional(readOnly = true)
    public List<InscriptionResponse> listByFormation(Long formationId) {
        requireStaff();

        List<Inscription> inscriptions =
            inscriptionRepository.findByFormationIdOrderByDateInscriptionAsc(formationId);
        if (inscriptions.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = inscriptions.stream().map(Inscription::getUserId).toList();
        Map<Long, UserDto> byId;
        try {
            byId = userClient.getByIds(userIds).stream()
                .collect(Collectors.toMap(UserDto::getId, Function.identity()));
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service unreachable");
        }

        return inscriptions.stream()
            .map(i -> {
                UserDto u = byId.get(i.getUserId());
                return new InscriptionResponse(
                    i.getUserId(),
                    // A user deleted in the other service leaves the enrolment behind.
                    u == null ? "(compte supprimé)" : u.getUsername(),
                    u == null ? "" : u.getEmail(),
                    i.getDateInscription());
            })
            .toList();
    }

    /** Which formations one user is enrolled in — the admin's per-user view. */
    @Transactional(readOnly = true)
    public List<Formation> formationsOfUser(Long userId) {
        UserDto caller = currentUserService.currentUser();
        if (!"ADMIN".equals(caller.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Réservé à l'administrateur");
        }
        return inscriptionRepository.findByUserId(userId).stream()
            .map(Inscription::getFormation)
            .toList();
    }

    /** Creating and editing a formation is open to admins and trainers. */
    public void requireStaff() {
        UserDto user = currentUserService.currentUser();
        if (user.getRole() == null || !STAFF_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Réservé à un admin ou un formateur");
        }
    }
}
