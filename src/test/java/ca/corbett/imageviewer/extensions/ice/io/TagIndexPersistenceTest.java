package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;
import ca.corbett.imageviewer.extensions.ice.TagList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagIndexPersistenceTest {

    @BeforeAll
    public static void setup() {
        IceExtension.extInfo = new AppExtensionInfo.Builder("Test")
                .setVersion("2.2.1")
                .build(); // cheesy, but we need it for these unit tests
    }

    @Test
    public void loadLegacyFormat_withValidFile_shouldLoad() throws Exception {
        // GIVEN a valid tag index in the old format (2.2.0)
        final String tagIndex = """
                /tmp/image1.jpg|/tmp/image1.ice|30|0|hello,there
                /tmp/image2.jpg|/tmp/image2.ice|40|1|hello,again
                """;
        File indexFile = File.createTempFile("TagIndexTest", ".txt");
        indexFile.deleteOnExit();
        FileSystemUtil.writeStringToFile(tagIndex,indexFile);

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
    public void saveLegacyFormat_withData_shouldSaveAndLoad() throws Exception {
        // GIVEN some tag index entries:
        List<TagIndexEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(generateIndexEntry(i));
        }

        // WHEN we save them and then load them back:
        File indexFile = File.createTempFile("TagIndexTest", ".txt");
        indexFile.deleteOnExit();
        TagIndexPersistence.save(entries, indexFile);
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