package tn.esprit.formation.service;

import org.springframework.stereotype.Component;
import tn.esprit.formation.entity.FormationImage;

/**
 * The implementation in use: the bytes ride along in the image's own row.
 *
 * <p>At this project's scale (tens of images, a megabyte in total) this is the simplest
 * thing that works — one backup covers everything, there are no orphan files, and the
 * image commits in the same transaction as its row.
 */
@Component
public class DatabaseImageStorage implements FormationImageStorage {

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
