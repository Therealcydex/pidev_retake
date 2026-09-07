package tn.esprit.formation.service;

import org.springframework.stereotype.Component;
import tn.esprit.formation.entity.FormationImage;

/**
 * Where an image's bytes live.
 *
 * <p>The metadata row — filename, content type, which formation — stays in MySQL under
 * any implementation. Only the bytes move. Today they ride along in the same row
 * ({@link DatabaseImageStorage} below); at a scale where that stopped being reasonable
 * they would live in a bucket and the row would keep the object key instead.
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

    /**
     * The implementation in use: the bytes ride along in the image's own row.
     *
     * <p>At this project's scale (tens of images, a megabyte in total) this is the
     * simplest thing that works — one backup covers everything, there are no orphan
     * files, and the image commits in the same transaction as its row.
     */
    @Component
    class DatabaseImageStorage implements FormationImageStorage {

        @Override
        public void store(FormationImage image, byte[] bytes) {
            image.setData(bytes);
        }

        @Override
        public byte[] load(FormationImage image) {
            return image.getData();
        }

        @Override
        public void delete(FormationImage image) {
            // Nothing to do: the row carries the bytes, so deleting the row removes them.
            // A bucket-backed implementation would delete the object here.
        }
    }
}
