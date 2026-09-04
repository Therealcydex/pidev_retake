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
import tn.esprit.formation.repository.ChapitreRepository;
import tn.esprit.formation.repository.FormationRepository;
import tn.esprit.formation.repository.InscriptionRepository;

import java.util.List;
import java.util.Map;

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
    @Autowired private ChapitreRepository chapitreRepository;
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
    @DisplayName("Bean Validation rejects a brief description over 100 characters")
    void descriptionIsValidatedAtTheApiBoundary() throws Exception {
        callerIs(TRAINER_A, "TRAINER");

        mvc.perform(post("/formations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Spring Boot", "x".repeat(101))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "La description brève ne peut pas dépasser 100 caractères"));
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

    /* ---------- chapters inherit the formation's ownership rule ---------- */

    private String chapitreBody(String titre, Long formationId) throws Exception {
        return json.writeValueAsString(
            Map.of("titre", titre, "contenu", "contenu", "formationId", formationId));
    }

    private Long createChapitreAs(long userId, String role, Long formationId) throws Exception {
        callerIs(userId, role);
        String response = mvc.perform(post("/chapitres")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Chapitre", formationId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("a trainee cannot add a chapter")
    void traineeCannotAddAChapter() throws Exception {
        Long formationId = createFormationAs(TRAINER_A);

        callerIs(TRAINEE_ID, "TRAINEE");
        mvc.perform(post("/chapitres")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Chapitre", formationId)))
            .andExpect(status().isForbidden());

        assertThat(chapitreRepository.findByFormationId(formationId)).isEmpty();
    }

    @Test
    @DisplayName("a trainer cannot add a chapter to another trainer's formation")
    void trainerCannotAddAChapterElsewhere() throws Exception {
        Long formationId = createFormationAs(TRAINER_A);

        callerIs(TRAINER_B, "TRAINER");
        mvc.perform(post("/chapitres")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Chapitre", formationId)))
            .andExpect(status().isForbidden());

        assertThat(chapitreRepository.findByFormationId(formationId)).isEmpty();
    }

    @Test
    @DisplayName("the owner and an admin may both add a chapter")
    void ownerAndAdminCanAddAChapter() throws Exception {
        Long formationId = createFormationAs(TRAINER_A);

        createChapitreAs(TRAINER_A, "TRAINER", formationId);
        createChapitreAs(ADMIN_ID, "ADMIN", formationId);

        assertThat(chapitreRepository.findByFormationId(formationId)).hasSize(2);
    }

    @Test
    @DisplayName("a trainer cannot edit or delete another trainer's chapter")
    void trainerCannotTouchAnotherTrainersChapter() throws Exception {
        Long formationId = createFormationAs(TRAINER_A);
        Long chapitreId = createChapitreAs(TRAINER_A, "TRAINER", formationId);

        callerIs(TRAINER_B, "TRAINER");

        mvc.perform(put("/chapitres/" + chapitreId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Détourné", formationId)))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/chapitres/" + chapitreId))
            .andExpect(status().isForbidden());

        assertThat(chapitreRepository.findById(chapitreId))
            .get()
            .extracting(c -> c.getTitre())
            .isEqualTo("Chapitre");
    }

    /**
     * The update call reassigns the formation from the request body, so it can move a
     * chapter. Checking only the target formation would let a trainer pull someone else's
     * chapter into their own; checking only the current one would let them push their own
     * chapter into a formation they do not own.
     */
    @Test
    @DisplayName("a trainer cannot move a chapter across an ownership boundary")
    void trainerCannotMoveAChapterAcrossOwnership() throws Exception {
        Long formationA = createFormationAs(TRAINER_A);
        Long formationB = createFormationAs(TRAINER_B);
        Long chapitreOfA = createChapitreAs(TRAINER_A, "TRAINER", formationA);
        Long chapitreOfB = createChapitreAs(TRAINER_B, "TRAINER", formationB);

        callerIs(TRAINER_B, "TRAINER");

        // Pulling A's chapter into B's own formation.
        mvc.perform(put("/chapitres/" + chapitreOfA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Volé", formationB)))
            .andExpect(status().isForbidden());

        // Pushing B's own chapter into A's formation.
        mvc.perform(put("/chapitres/" + chapitreOfB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(chapitreBody("Déposé", formationA)))
            .andExpect(status().isForbidden());

        assertThat(chapitreRepository.findByFormationId(formationA)).hasSize(1);
        assertThat(chapitreRepository.findByFormationId(formationB)).hasSize(1);
    }

    @Test
    @DisplayName("deleting a chapter that does not exist is a 404, not a 500")
    void deletingAnUnknownChapterIsNotFound() throws Exception {
        callerIs(ADMIN_ID, "ADMIN");
        mvc.perform(delete("/chapitres/9999")).andExpect(status().isNotFound());
    }

    /* ---------- categories are shared reference data: admin only ---------- */

    @Test
    @DisplayName("a trainer cannot create, rename or delete a category")
    void trainerCannotManageCategories() throws Exception {
        callerIs(TRAINER_A, "TRAINER");

        mvc.perform(post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Nouvelle\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(put("/categories/" + categorieId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Renommée\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/categories/" + categorieId))
            .andExpect(status().isForbidden());

        assertThat(categorieRepository.findById(categorieId))
            .get()
            .extracting(c -> c.getNom())
            .isEqualTo("IT");
    }

    @Test
    @DisplayName("an admin may manage categories, and anyone may read them")
    void adminManagesCategoriesAndReadingStaysOpen() throws Exception {
        mvc.perform(get("/categories")).andExpect(status().isOk());

        callerIs(ADMIN_ID, "ADMIN");
        mvc.perform(post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Nouvelle\"}"))
            .andExpect(status().isCreated());

        assertThat(categorieRepository.findAll()).hasSize(2);
    }
}
