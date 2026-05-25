package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TagIndexMigration.
 */
class TagIndexMigrationTest {

    @TempDir
    File tempDir;

    @BeforeAll
    public static void setup() {
        IceExtension.extInfo = new AppExtensionInfo.Builder("Test")
                .setVersion("2.2.1")
                .build();
    }

    @AfterAll
    public static void tearDown() {
        TagIndexPersistence.closeConnection();
    }

    @Test
    public void migrate_currentFormat_legacyIce_shouldMigrateToSqlite() throws Exception {
        // GIVEN a current-format (2.2.1) legacy index file
        String legacyContent = """
                ICE_tag_index|2.2.1
                LOC|0|/tmp/test
                0|test.jpg|100|5000|hello, world
                """;
        File legacyFile = new File(tempDir, "tagIndex.ice");
        FileSystemUtil.writeStringToFile(legacyContent, legacyFile);
        File dbFile = new File(tempDir, "tagIndex.db");
        assertFalse(dbFile.exists());

        // WHEN we migrate
        TagIndexMigration.migrate(legacyFile, dbFile);

        // THEN the DB should exist with the migrated entries
        assertTrue(dbFile.exists());
        List<TagIndexEntry> entries = TagIndexPersistence.loadSqlite(dbFile);
        assertEquals(1, entries.size());
        assertEquals("/tmp/test/test.jpg", entries.get(0).getImageFile().getAbsolutePath());
        assertEquals("hello, world", entries.get(0).getTagList().toString());
    }

    @Test
    public void migrate_legacy220Format_shouldMigrateToSqlite() throws Exception {
        // GIVEN a legacy 2.2.0 format index file
        String legacyContent = """
                /tmp/test/image1.jpg|/tmp/test/image1.ice|30|0|foo,bar
                /tmp/test/image2.jpg|/tmp/test/image2.ice|40|1|baz,qux
                """;
        File legacyFile = new File(tempDir, "tagIndex.ice");
        FileSystemUtil.writeStringToFile(legacyContent, legacyFile);
        File dbFile = new File(tempDir, "tagIndex.db");
        assertFalse(dbFile.exists());

        // WHEN we migrate
        TagIndexMigration.migrate(legacyFile, dbFile);

        // THEN the DB should exist with the migrated entries
        assertTrue(dbFile.exists());
        List<TagIndexEntry> entries = TagIndexPersistence.loadSqlite(dbFile);
        assertEquals(2, entries.size());
        assertEquals("/tmp/test/image1.jpg", entries.get(0).getImageFile().getAbsolutePath());
        assertEquals("/tmp/test/image2.jpg", entries.get(1).getImageFile().getAbsolutePath());
        assertEquals("foo, bar", entries.get(0).getTagList().toString());
        assertEquals("baz, qux", entries.get(1).getTagList().toString());
    }

    @Test
    public void migrate_withNonExistentLegacyFile_shouldThrow() throws Exception {
        // GIVEN a non-existent legacy file
        File legacyFile = new File(tempDir, "nonexistent.ice");
        File dbFile = new File(tempDir, "tagIndex.db");

        // WHEN we migrate
        IOException exception = assertThrows(IOException.class, () ->
                TagIndexMigration.migrate(legacyFile, dbFile));

        // THEN it should report the file doesn't exist
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void migrate_successfulRename_createsMigratedFile() throws Exception {
        // GIVEN a legacy file
        String legacyContent = "ICE_tag_index|2.2.1\nLOC|0|/tmp\n0|img.jpg|0|0|\n";
        File legacyFile = new File(tempDir, "tagIndex.ice");
        FileSystemUtil.writeStringToFile(legacyContent, legacyFile);
        File dbFile = new File(tempDir, "tagIndex.db");

        // WHEN we migrate
        TagIndexMigration.migrate(legacyFile, dbFile);

        // THEN the legacy file should be renamed to .migrated
        File migratedFile = new File(tempDir, "tagIndex.ice.migrated");
        assertTrue(migratedFile.exists());
        assertFalse(legacyFile.exists());
    }

    @Test
    public void migrate_emptyLegacyFile_shouldCreateEmptySqlite() throws Exception {
        // GIVEN an empty legacy file (header only)
        String legacyContent = "ICE_tag_index|2.2.1\n";
        File legacyFile = new File(tempDir, "tagIndex.ice");
        FileSystemUtil.writeStringToFile(legacyContent, legacyFile);
        File dbFile = new File(tempDir, "tagIndex.db");

        // WHEN we migrate
        TagIndexMigration.migrate(legacyFile, dbFile);

        // THEN the DB should exist with 0 entries
        assertTrue(dbFile.exists());
        List<TagIndexEntry> entries = TagIndexPersistence.loadSqlite(dbFile);
        assertEquals(0, entries.size());
    }

    @Test
    public void migrate_multipleEntries_shouldPreserveAllData() throws Exception {
        // GIVEN a legacy file with many entries
        StringBuilder legacyContent = new StringBuilder();
        legacyContent.append("ICE_tag_index|2.2.1\n");
        legacyContent.append("LOC|0|/tmp/migration\n");
        for (int i = 0; i < 100; i++) {
            legacyContent.append(String.format("0|image%d.jpg|%d|%d|tag1, tag2, tag3\n", i, 50 + i, 1000L * i));
        }
        File legacyFile = new File(tempDir, "tagIndex.ice");
        FileSystemUtil.writeStringToFile(legacyContent.toString(), legacyFile);
        File dbFile = new File(tempDir, "tagIndex.db");

        // WHEN we migrate
        TagIndexMigration.migrate(legacyFile, dbFile);

        // THEN all entries should be present in the DB
        assertTrue(dbFile.exists());
        List<TagIndexEntry> entries = TagIndexPersistence.loadSqlite(dbFile);
        assertEquals(100, entries.size());
        // Spot-check a few entries
        assertEquals("/tmp/migration/image0.jpg", entries.get(0).getImageFile().getAbsolutePath());
        assertEquals(50, entries.get(0).getTagFileSize());
        assertEquals(0, entries.get(0).getTagFileLastModified());
        assertEquals("/tmp/migration/image99.jpg", entries.get(99).getImageFile().getAbsolutePath());
        assertEquals(149, entries.get(99).getTagFileSize());
    }
}
