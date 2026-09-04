package tn.esprit.formation.service;

import tn.esprit.formation.entity.FormationImage;

/**
 * Where an image's bytes live.
 *
 * <p>The metadata row — filename, content type, which formation — stays in MySQL under
 * any implementation. Only the bytes move. Today they ride along in the same row
 * ({@link DatabaseImageStorage}); at a scale where that stopped being reasonable they
 * would live in a bucket and the row would keep the object key instead.
 *
 * <p>That is the whole point of this interface: the storage decision is made in one
 * place rather than spread through the service, so changing it is writing a second
 * implementation, not reworking the feature.
 */
public interface FormationImageStorage {

    /** Attach the bytes to an image that is about to be saved. */
    void store(FormationImage image, byte[] bytes);

    /** The bytes for an image, ready to be written to the response. */
    byte[] load(FormationImage image);

    /** Drop the bytes for an image whose row is about to be deleted. */
    void delete(FormationImage image);
}
