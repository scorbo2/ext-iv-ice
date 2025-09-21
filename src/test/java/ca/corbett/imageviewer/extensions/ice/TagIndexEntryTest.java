package ca.corbett.imageviewer.extensions.ice;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class TagIndexEntryTest {

    @Test
    public void tagIndexEntry_toAndFromString_shouldParse() {
        // GIVEN a TagIndexEntry with data:
        TagIndexEntry entry = new TagIndexEntry();
        entry.setImageFile(new File("test.jpg"));
        entry.setTagFile(new File("test.ice"));
        entry.setTagFileLastModified(1);
        entry.setTagFileSize(2);
        entry.setTagList(TagList.of("hello,there"));

        // WHEN we take its string value:
        String actual = entry.toString();

        // THEN we should be able to create a new entry from that string:
        TagIndexEntry newEntry = new TagIndexEntry(actual);
        assertEquals(entry, newEntry);
    }

    @Test
    public void fromString_withValidString_shouldParse() {
        // GIVEN a valid persisted index entry in string form:
        String validInput = "/tmp/test.jpg|/tmp/test.ice|0|1|hello,there";

        // WHEN we parse it:
        TagIndexEntry entry = new TagIndexEntry(validInput);

        // THEN it should look as expected:
        assertEquals("/tmp/test.jpg", entry.getImageFilePath());
        assertEquals("/tmp/test.ice", entry.getTagFilePath());
        assertEquals(0, entry.getTagFileSize());
        assertEquals(1, entry.getTagFileLastModified());
        assertEquals("hello, there", entry.getTagList().toString());
    }
}