package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates one-time migration from legacy pipe-separated index files to SQLite.
 * <p>
 * The migration process:
 * </p>
 * <ol>
 * <li>Parse legacy index file using existing TagIndexPersistence logic.</li>
 * <li>Bulk insert migrated entries into SQLite in a single transaction.</li>
 * <li>Rename legacy file to .migrated only after successful commit.</li>
 * </ol>
 * <p>
 * If migration fails, the legacy file is left untouched for retry/inspection.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ICE 3.5.0
 */
public class TagIndexMigration {

    private static final Logger log = Logger.getLogger(TagIndexMigration.class.getName());

    private static final String MIGRATED_SUFFIX = ".migrated";

    /**
     * Performs one-time migration from a legacy index file to SQLite.
     * <p>
     * Steps:
     * <ol>
     *   <li>Parse legacy index file using existing TagIndexPersistence.load()</li>
     *   <li>Bulk insert all entries into SQLite in a single transaction</li>
     *   <li>Rename legacy file to .migrated only after full success</li>
     * </ol>
     *
     * @param legacyFile The legacy pipe-separated index file (e.g., tagIndex.ice).
     * @param dbFile     The target SQLite database file path (e.g., tagIndex.db).
     * @throws IOException if parsing, inserting, or renaming fails.
     */
    public static void migrate(File legacyFile, File dbFile) throws IOException {
        if (!legacyFile.exists()) {
            throw new IOException("Legacy index file does not exist: " + legacyFile.getAbsolutePath());
        }

        // Step 1: Parse legacy index file
        List<TagIndexEntry> entries = TagIndexPersistence.load(legacyFile);
        log.info("IceExtension: parsed " + entries.size() + " entries from legacy index file.");

        // Step 2: Bulk insert into SQLite
        TagIndexPersistence.save(entries, dbFile);
        log.info("IceExtension: inserted " + entries.size() + " entries into SQLite database.");

        // Step 3: Rename legacy file only after successful commit
        File migratedFile = new File(legacyFile.getParentFile(), legacyFile.getName() + MIGRATED_SUFFIX);
        if (!legacyFile.renameTo(migratedFile)) {
            // If rename fails, log but don't throw - the data is safely in SQLite
            log.log(Level.WARNING, "IceExtension: could not rename legacy index file to " + migratedFile.getAbsolutePath()
                    + ". Please rename manually: " + legacyFile.getAbsolutePath());
        } else {
            log.info("IceExtension: legacy index file renamed to " + migratedFile.getAbsolutePath());
        }
    }
}
