package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** The chapter payloads, grouped in one file. The JSON is unchanged. */
public final class ChapitreDtos {

    private ChapitreDtos() {
    }

    @Getter
    @Setter
    public static class Request {
        private String titre;
        private String contenu;
        private Long formationId;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String titre;
        private String contenu;
        private Long formationId;
    }
}
