package tn.esprit.formation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The illustration attached to a formation, kept in its own table so that listing
 * formations never drags the image bytes into memory.
 */
@Entity
@Getter
@Setter
@Table(name = "formation_images")
public class FormationImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    private String contentType;

    /** MEDIUMBLOB (16 MB) — well above the 5 MB upload cap, and signals the intended scale. */
    @Lob
    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @OneToOne
    @JoinColumn(name = "formation_id", unique = true)
    private Formation formation;
}
