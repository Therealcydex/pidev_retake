package tn.esprit.formation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.formation.client.UserClient;
import tn.esprit.formation.client.UserDto;
import tn.esprit.formation.dto.FormationRequest;
import tn.esprit.formation.entity.Categorie;
import tn.esprit.formation.entity.Niveau;
import tn.esprit.formation.repository.CategorieRepository;
import tn.esprit.formation.repository.FormationRepository;
import tn.esprit.formation.repository.InscriptionRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests: a real Spring context, the real controller → service → repository
 * chain, real JSON serialisation, real Bean Validation and a real SQL database (H2).
 *
 * Only one thing is faked: UserClient, the Feign boundary to the USER microservice.
 * That service is not running during a build, and stubbing it is also how each test
 * chooses who is calling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FormationIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final long TRAINER_A = 10L;
    private static final long TRAINER_B = 11L;
    private static final long TRAINEE_ID = 20L;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CategorieRepository categorieRepository;
    @Autowired private FormationRepository formationRepository;
    @Autowired private InscriptionRepository inscriptionRepository;

    @MockBean private UserClient userClient;

    private Long categorieId;

    @BeforeEach
    void setUp() {
        inscriptionRepository.deleteAll();
        formationRepository.deleteAll();
        categorieRepository.deleteAll();

        Categorie categorie = new Categorie();
        categorie.setNom("IT");
        categorieId = categorieRepository.save(categorie).getId();
    }

    /** Every request resolves its caller through UserClient.me(); this decides who that is. */
    private void callerIs(long id, String role) {
        when(userClient.me()).thenReturn(new UserDto(id, "user" + id, "user" + id + "@esprit.tn", role));
    }

    private String body(String titre, String description) throws Exception {
        FormationRequest r = new FormationRequest();
        r.setTitre(titre);
        r.setDescription(description);
        r.setNiveau(Niveau.DEBUTANT);
        r.setCategorieId(categorieId);
        return json.writeValueAsString(r);
    }

    private Long createFormationAs(long userId) throws Exception {
        callerIs(userId, "TRAINER");
        String response = mvc.perform(post("/formations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Spring Boot", "APIs REST")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("creating a formation stamps the caller as its owner")
    void createStampsOwner() throws Exception {
        callerIs(TRAINER_A, "TRAINER");

        mvc.perform(post("/formations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Spring Boot", "APIs REST")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ownerId").value(TRAINER_A))
            .andExpect(jsonPath("$.titre").value("Spring Boot"));

        assertThat(formationRepository.findAll()).hasSize(1);
        assertThat(formationRepository.findAll().get(0).getOwnerId()).isEqualTo(TRAINER_A);
    }

    @Test
    @DisplayName("a trainee cannot create a formation")
    void traineeCannotCreate() throws Exception {
        callerIs(TRAINEE_ID, "TRAINEE");

        mvc.perform(post("/formations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Nope", "x")))
            .andExpect(status().isForbidden());

        assertThat(formationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Bean Validation rejects a brief description over 50 characters")
    void descriptionIsValidatedAtTheApiBoundary() throws Exception {
        callerIs(TRAINER_A, "TRAINER");

        mvc.perform(post("/formations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Spring Boot", "x".repeat(51))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "La description brève ne peut pas dépasser 50 caractères"));
    }

    @Test
    @DisplayName("deleting a formation also removes the enrolments that point at it")
    void deletingAFormationRemovesItsEnrolments() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINEE_ID, "TRAINEE");
        mvc.perform(post("/formations/" + id + "/inscription"))
            .andExpect(status().isCreated());
        assertThat(inscriptionRepository.findByUserId(TRAINEE_ID)).hasSize(1);

        // Without the cleanup in FormationService.delete, the foreign key on
        // inscriptions.formation_id rejects this and the caller sees a 500.
        callerIs(TRAINER_A, "TRAINER");
        mvc.perform(delete("/formations/" + id))
            .andExpect(status().isNoContent());

        assertThat(formationRepository.findById(id)).isEmpty();
        assertThat(inscriptionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a trainer may edit their own formation but not another trainer's")
    void ownershipGovernsEditing() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINER_B, "TRAINER");
        mvc.perform(put("/formations/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Hijacked", "nope")))
            .andExpect(status().isForbidden());

        callerIs(TRAINER_A, "TRAINER");
        mvc.perform(put("/formations/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Spring Boot 3", "APIs REST")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.titre").value("Spring Boot 3"));

        callerIs(ADMIN_ID, "ADMIN");
        mvc.perform(put("/formations/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Renamed by admin", "APIs REST")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a trainee enrols, and the enrolment reaches the database")
    void traineeEnrols() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINEE_ID, "TRAINEE");
        mvc.perform(post("/formations/" + id + "/inscription"))
            .andExpect(status().isCreated());

        assertThat(inscriptionRepository.findByUserId(TRAINEE_ID)).hasSize(1);

        mvc.perform(get("/formations/mes-inscriptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value(id));
    }

    @Test
    @DisplayName("enrolling twice is a conflict")
    void enrollingTwiceConflicts() throws Exception {
        Long id = createFormationAs(TRAINER_A);
        callerIs(TRAINEE_ID, "TRAINEE");

        mvc.perform(post("/formations/" + id + "/inscription")).andExpect(status().isCreated());
        mvc.perform(post("/formations/" + id + "/inscription")).andExpect(status().isConflict());

        assertThat(inscriptionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a trainer cannot enrol")
    void trainerCannotEnrol() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINER_B, "TRAINER");
        mvc.perform(post("/formations/" + id + "/inscription"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unenrolling removes the row; doing it twice is a 404")
    void unenrol() throws Exception {
        Long id = createFormationAs(TRAINER_A);
        callerIs(TRAINEE_ID, "TRAINEE");

        mvc.perform(post("/formations/" + id + "/inscription")).andExpect(status().isCreated());
        mvc.perform(delete("/formations/" + id + "/inscription")).andExpect(status().isNoContent());
        mvc.perform(delete("/formations/" + id + "/inscription")).andExpect(status().isNotFound());

        assertThat(inscriptionRepository.findAll()).isEmpty();
    }

    /**
     * The roster crosses the service boundary: ids come from this database, names from
     * the USER service. Both halves are exercised here.
     */
    @Test
    @DisplayName("the owner sees the roster with names resolved from the USER service")
    void rosterJoinsLocalIdsWithRemoteNames() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINEE_ID, "TRAINEE");
        mvc.perform(post("/formations/" + id + "/inscription")).andExpect(status().isCreated());

        callerIs(TRAINER_A, "TRAINER");
        when(userClient.getByIds(List.of(TRAINEE_ID)))
            .thenReturn(List.of(new UserDto(TRAINEE_ID, "amine", "amine@esprit.tn", "TRAINEE")));

        mvc.perform(get("/formations/" + id + "/inscriptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(TRAINEE_ID))
            .andExpect(jsonPath("$[0].username").value("amine"))
            .andExpect(jsonPath("$[0].email").value("amine@esprit.tn"));
    }

    @Test
    @DisplayName("a trainer cannot see the roster of a formation they do not own")
    void rosterIsOwnerOnly() throws Exception {
        Long id = createFormationAs(TRAINER_A);

        callerIs(TRAINER_B, "TRAINER");
        mvc.perform(get("/formations/" + id + "/inscriptions"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the catalogue is readable and carries the fields the cards need")
    void catalogueExposesCardFields() throws Exception {
        createFormationAs(TRAINER_A);

        mvc.perform(get("/formations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].titre").value("Spring Boot"))
            .andExpect(jsonPath("$[0].categorieNom").value("IT"))
            .andExpect(jsonPath("$[0].ownerId").value(TRAINER_A))
            .andExpect(jsonPath("$[0].chapitreCount").value(0))
            .andExpect(jsonPath("$[0].hasImage").value(false));
    }

    @Test
    @DisplayName("an unknown formation is a 404")
    void unknownFormationIsNotFound() throws Exception {
        mvc.perform(get("/formations/9999")).andExpect(status().isNotFound());
    }
}
