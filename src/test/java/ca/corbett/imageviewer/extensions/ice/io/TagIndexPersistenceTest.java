package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;
import ca.corbett.imageviewer.extensions.ice.TagList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagIndexPersistenceTest {

    @TempDir
    File tempDir;

    @BeforeAll
    public static void setup() {
        IceExtension.extInfo = new AppExtensionInfo.Builder("Test")
                .setVersion("2.2.1")
                .build(); // cheesy, but we need it for these unit tests
    }

    @AfterAll
    public static void tearDown() {
        // Clean up SQLite connection after all tests
        TagIndexPersistence.closeConnection();
    }

    @Test
    public void loadLegacyFormat_withValidFile_shouldLoad() throws Exception {
        // GIVEN a valid tag index in the old format (2.2.0)
        final String tagIndex = """
                /tmp/image1.jpg|/tmp/image1.ice|30|0|hello,there
                /tmp/image2.jpg|/tmp/image2.ice|40|1|hello,again
                """;
        File indexFile = new File(tempDir, "legacy.ice");
        FileSystemUtil.writeStringToFile(tagIndex, indexFile);

        // WHEN we try to parse it:
        assertEquals("2.2.0", TagIndexPersistence.getIndexVersion(indexFile));
        List<TagIndexEntry> indexEntries = TagIndexPersistence.load(indexFile);
        assertEquals(2, indexEntries.size());
        assertEquals("/tmp/image1.jpg", indexEntries.get(0).getImageFile().getAbsolutePath());
        assertEquals("/tmp/image2.jpg", indexEntries.get(1).getImageFile().getAbsolutePath());
        assertEquals("hello, there", indexEntries.get(0).getTagList().toString());
        assertEquals("hello, again", indexEntries.get(1).getTagList().toString());
        assertEquals("/tmp/image1.ice", indexEntries.get(0).getTagFile().getAbsolutePath());
        assertEquals("/tmp/image2.ice", indexEntries.get(1).getTagFile().getAbsolutePath());
        assertEquals(30, indexEntries.get(0).getTagFileSize());
        assertEquals(40, indexEntries.get(1).getTagFileSize());
        assertEquals(0, indexEntries.get(0).getTagFileLastModified());
        assertEquals(1, indexEntries.get(1).getTagFileLastModified());
    }

    @Test
    public void saveCurrentFormat_withData_shouldSaveAndLoad() throws Exception {
        // GIVEN some tag index entries:
        List<TagIndexEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(generateIndexEntry(i));
        }

        // WHEN we save them to SQLite and then load them back:
        File dbFile = new File(tempDir, "tagIndex.db");
        TagIndexPersistence.save(entries, dbFile);
        entries = TagIndexPersistence.loadSqlite(dbFile);

        // THEN we should see our entries:
        assertEquals(5, entries.size());
        for (int i = 0; i < 5; i++) {
            validateIndexEntry(entries.get(i), i);
        }
    }

    @Test
    public void sqlite_saveAndLoad_withEmptyList_shouldSucceed() throws Exception {
        // GIVEN an empty list of entries
        List<TagIndexEntry> entries = new ArrayList<>();

        // WHEN we save and load
        File dbFile = new File(tempDir, "empty.db");
        TagIndexPersistence.save(entries, dbFile);
        entries = TagIndexPersistence.loadSqlite(dbFile);

        // THEN we should see 0 entries
        assertEquals(0, entries.size());
    }

    @Test
    public void sqlite_saveTwice_shouldReplaceAllEntries() throws Exception {
        // GIVEN some initial entries
        List<TagIndexEntry> entries1 = new ArrayList<>();
        entries1.add(generateIndexEntry(0));
        entries1.add(generateIndexEntry(1));

        File dbFile = new File(tempDir, "replace.db");
        TagIndexPersistence.save(entries1, dbFile);

        // WHEN we save a different set of entries
        List<TagIndexEntry> entries2 = new ArrayList<>();
        entries2.add(generateIndexEntry(2));

        TagIndexPersistence.save(entries2, dbFile);
        entries2 = TagIndexPersistence.loadSqlite(dbFile);

        // THEN we should only see the second set
        assertEquals(1, entries2.size());
        assertEquals("/tmp/image2.jpg", entries2.get(0).getImageFile().getAbsolutePath());
    }

    @Test
    public void sqlite_schemaInitialized_onFirstLoad() throws Exception {
        // GIVEN a SQLite database path that doesn't exist yet
        File dbFile = new File(tempDir, "new.db");
        assertFalse(dbFile.exists());

        // WHEN we load from it
        List<TagIndexEntry> entries = TagIndexPersistence.loadSqlite(dbFile);

        // THEN the file should exist and contain 0 entries
        assertTrue(dbFile.exists());
        assertEquals(0, entries.size());
    }

    @Test
    public void saveLegacy_withData_shouldSaveAndLoad() throws Exception {
        // GIVEN some tag index entries:
        List<TagIndexEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(generateIndexEntry(i));
        }

        // WHEN we save them using legacy format and then load them back:
        File indexFile = new File(tempDir, "legacy.ice");
        TagIndexPersistence.saveLegacy(entries, indexFile);
        entries = TagIndexPersistence.load(indexFile);

        // THEN we should see our entries:
        assertEquals(5, entries.size());
        for (int i = 0; i < 5; i++) {
            validateIndexEntry(entries.get(i), i);
        }
    }

    private void validateIndexEntry(TagIndexEntry entry, int number) {
        assertNotNull(entry);
        assertNotNull(entry.getImageFile());
        assertNotNull(entry.getTagFile());
        assertNotNull(entry.getTagList());
        assertEquals("/tmp/image"+number+".jpg", entry.getImageFile().getAbsolutePath());
        assertEquals("/tmp/image"+number+".ice", entry.getTagFile().getAbsolutePath());
        assertEquals(100+number, entry.getTagFileSize());
        assertEquals(number, entry.getTagFileLastModified());
        assertEquals("hello, there, donkey", entry.getTagList().toString());
    }

    private TagIndexEntry generateIndexEntry(int number) {
        TagIndexEntry entry = new TagIndexEntry();
        entry.setImageFile(new File("/tmp/image"+number+".jpg"));
        entry.setTagFile(new File("/tmp/image"+number+".ice"));
        entry.setTagFileSize(100 + number);
        entry.setTagFileLastModified(number);
        entry.setTagList(TagList.of("hello, there, donkey"));
        return entry;
    }
}
