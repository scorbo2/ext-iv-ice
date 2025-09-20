package ca.corbett.imageviewer.extensions.ice;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class TagIndexTest {

    @Test
    public void tagIndexEntry_toAndFromString_shouldParse() {
        // GIVEN a TagIndexEntry with data:
        TagIndex.TagIndexEntry entry = new TagIndex.TagIndexEntry();
        entry.setImageFile(new File("test.jpg"));
        entry.setLastModified(1);
        entry.setTagFileSize(2);
        entry.setTagList(TagList.of("hello,there"));

        // WHEN we take its string value:
        String actual = entry.toString();

        // THEN we should be able to create a new entry from that string:
        TagIndex.TagIndexEntry newEntry = new TagIndex.TagIndexEntry(actual);
        assertEquals(entry, newEntry);
    }

}