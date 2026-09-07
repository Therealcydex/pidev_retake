package tn.esprit.formation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** The category payloads, grouped in one file. The JSON is unchanged. */
public final class CategorieDtos {

    private CategorieDtos() {
    }

    @Getter
    @Setter
    public static class Request {
        private String nom;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String nom;
    }
}
