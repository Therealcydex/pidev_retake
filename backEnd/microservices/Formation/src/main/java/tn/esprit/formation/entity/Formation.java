package tn.esprit.formation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "formations")
public class Formation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    /** Short blurb shown on the catalogue card; the schema enforces the same 100 as the DTO. */
    @Column(length = 100)
    private String description;

    /** Long-form text shown only on the detail page; TEXT rather than the default VARCHAR(255). */
    @Column(columnDefinition = "TEXT")
    private String descriptionDetaillee;

    /**
     * User id of the trainer (or admin) who created it. A plain Long, not a relation:
     * the user lives in another service and another database, so there is no foreign
     * key to point at — the same reasoning as Inscription.userId.
     */
    @Column(name = "owner_id")
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    private Niveau niveau;

    @ManyToOne
    private Categorie categorie;

    @OneToMany(mappedBy = "formation", cascade = CascadeType.ALL)
    private List<Chapitre> chapitres = new ArrayList<>();
}
