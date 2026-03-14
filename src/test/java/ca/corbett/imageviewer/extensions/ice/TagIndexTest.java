package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.extras.properties.PropertiesManager;
import ca.corbett.imageviewer.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the TagIndex class.
 * <p>
 * TagIndex methods require AppConfig to be initialized (specifically the isEnabled() check).
 * We use Mockito to mock AppConfig and inject it via the AppConfigProvider pattern,
 * allowing us to test the full functionality of TagIndex without requiring the entire
 * ImageViewer environment.
 */
class TagIndexTest {

    @TempDir
    Path tempDir;

    private static AppConfig appConfig;
    private static BooleanProperty enabledProp;
    private static PropertiesManager propsManager;
    private TagIndex tagIndex;

    @BeforeAll
    public static void setUpClass() {
        // Mock AppConfig so that TagIndex.isEnabled() always returns true for our tests
        appConfig = Mockito.mock(AppConfig.class);
        propsManager = Mockito.mock(PropertiesManager.class);
        enabledProp = new BooleanProperty(TagIndex.PROP_NAME, "isEnabled", true);
        Mockito.when(appConfig.getPropertiesManager()).thenReturn(propsManager);
        Mockito.when(propsManager.getProperty(TagIndex.PROP_NAME)).thenReturn(enabledProp);

        IceExtension.extInfo = new AppExtensionInfo.Builder("Test")
                .setVersion("2.2.1")
                .build();
    }

    @BeforeEach
    public void setUpTagIndex() {
        // Get a fresh instance for each test and inject our mocked AppConfig
        tagIndex = TagIndex.getInstance();
        tagIndex.setAppConfigProvider(() -> appConfig);
        
        // Set index file to temp directory to avoid overwriting real tagIndex.ice
        File tempIndexFile = new File(tempDir.toFile(), "tagIndex.ice");
        tagIndex.setIndexFile(tempIndexFile);
        
        tagIndex.clear(); // Ensure clean state
    }

    @AfterEach
    public void tearDown() {
        tagIndex.clear(); // Clean up after each test
        tagIndex.setAppConfigProvider(null); // Remove custom AppConfig provider
        enabledProp.setValue(true); // Reset to enabled if any test disabled it
    }

    @Test
    public void testGetInstance_shouldReturnSameInstance() {
        // GIVEN the singleton pattern
        // WHEN we get the instance twice
        TagIndex instance1 = TagIndex.getInstance();
        TagIndex instance2 = TagIndex.getInstance();

        // THEN they should be the same instance
        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    @Test
    public void testSize_withEmptyIndex_shouldReturnZero() {
        // GIVEN an empty tag index
        tagIndex.clear();
        
        // WHEN we check the size
        int size = tagIndex.size();

        // THEN it should be 0
        assertEquals(0, size);
    }

    @Test
    public void testSize_withEntries_shouldReturnCorrectCount() throws IOException {
        // GIVEN a tag index with some entries
        File imageFile1 = new File(tempDir.toFile(), "image1.jpg");
        File tagFile1 = createTestTagFile("tag1.ice", "hello, world");
        File imageFile2 = new File(tempDir.toFile(), "image2.jpg");
        File tagFile2 = createTestTagFile("tag2.ice", "foo, bar");

        // WHEN we add entries
        tagIndex.addOrUpdateEntry(imageFile1, tagFile1);
        tagIndex.addOrUpdateEntry(imageFile2, tagFile2);

        // THEN the size should reflect the number of entries
        assertEquals(2, tagIndex.size());
    }

    @Test
    public void testAddOrUpdateEntry_withNewEntry_shouldReturnNewEntryCreated() throws IOException {
        // GIVEN a new image and tag file
        File imageFile = new File(tempDir.toFile(), "newImage.jpg");
        File tagFile = createTestTagFile("newTag.ice", "hello, there");

        // WHEN we add the entry
        TagIndex.EntryAddResult result = tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // THEN it should indicate a new entry was created
        assertEquals(TagIndex.EntryAddResult.NewEntryCreated, result);
        assertEquals(1, tagIndex.size());
    }

    @Test
    public void testAddOrUpdateEntry_withExistingUpToDateEntry_shouldReturnSkippedBecauseUpToDate() throws IOException {
        // GIVEN an existing entry in the index
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we try to add the same entry again without changes
        TagIndex.EntryAddResult result = tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // THEN it should skip because it's up to date
        assertEquals(TagIndex.EntryAddResult.SkippedBecauseUpToDate, result);
        assertEquals(1, tagIndex.size());
    }

    @Test
    public void testAddOrUpdateEntry_withExistingModifiedEntry_shouldReturnExistingEntryUpdated() throws IOException {
        // GIVEN an existing entry in the index
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we modify the tag file and update
        FileSystemUtil.writeStringToFile("hello" + System.lineSeparator() + "universe", tagFile);
        long currentLastModified = tagFile.lastModified();
        boolean lastModifiedUpdated = tagFile.setLastModified(currentLastModified + 1000L); // add 1 second
        assertTrue(lastModifiedUpdated);
        TagIndex.EntryAddResult result = tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // THEN it should indicate the entry was updated
        assertEquals(TagIndex.EntryAddResult.ExistingEntryUpdated, result);
        assertEquals(1, tagIndex.size());
    }

    @Test
    public void testAddOrUpdateEntry_whenDisabled_shouldReturnSkippedBecauseDisabled() throws IOException {
        // GIVEN tag indexing is disabled
        enabledProp.setValue(false);
        File imageFile = new File(tempDir.toFile(), "newImage.jpg");
        File tagFile = createTestTagFile("newTag.ice", "hello, there");

        // WHEN we try to add the entry
        TagIndex.EntryAddResult result = tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // THEN it should skip because it's disabled
        assertEquals(TagIndex.EntryAddResult.SkippedBecauseDisabled, result);
        
        // Re-enable for other tests
        enabledProp.setValue(true);
    }

    @Test
    public void testRemoveEntry_withExistingEntry_shouldRemove() throws IOException {
        // GIVEN an entry in the index
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);
        assertEquals(1, tagIndex.size());

        // WHEN we remove the entry
        tagIndex.removeEntry(imageFile);

        // THEN the size should be 0
        assertEquals(0, tagIndex.size());
    }

    @Test
    public void testRemoveEntry_withNonExistingEntry_shouldDoNothing() {
        // GIVEN an empty index
        assertEquals(0, tagIndex.size());

        // WHEN we try to remove a non-existing entry
        File imageFile = new File(tempDir.toFile(), "nonExistent.jpg");
        tagIndex.removeEntry(imageFile);

        // THEN nothing should happen (no exception)
        assertEquals(0, tagIndex.size());
    }

    @Test
    public void testClear_shouldRemoveAllEntries() throws IOException {
        // GIVEN an index with entries
        File image1 = new File(tempDir.toFile(), "image1.jpg");
        File tag1 = createTestTagFile("tag1.ice", "hello, world");
        tagIndex.addOrUpdateEntry(image1, tag1);

        File image2 = new File(tempDir.toFile(), "image2.jpg");
        File tag2 = createTestTagFile("tag2.ice", "foo, bar");
        tagIndex.addOrUpdateEntry(image2, tag2);

        assertEquals(2, tagIndex.size());

        // WHEN we clear the index
        tagIndex.clear();

        // THEN the size should be 0
        assertEquals(0, tagIndex.size());
    }

    @Test
    public void testIsIndexedAndUpToDate_withNonExistentEntry_shouldReturnFalse() {
        // GIVEN an empty index
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = new File(tempDir.toFile(), "tag.ice");

        // WHEN we check if it's indexed
        boolean result = tagIndex.IsIndexedAndUpToDate(imageFile, tagFile);

        // THEN it should return false
        assertFalse(result);
    }

    @Test
    public void testIsIndexedAndUpToDate_withUpToDateEntry_shouldReturnTrue() throws IOException {
        // GIVEN an indexed entry
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it's indexed and up to date
        boolean result = tagIndex.IsIndexedAndUpToDate(imageFile, tagFile);

        // THEN it should return true
        assertTrue(result);
    }

    @Test
    public void testIsIndexedAndUpToDate_withOutdatedEntry_shouldUpdateAndReturnTrue() throws IOException {
        // GIVEN an indexed entry
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN the tag file is modified and we check if it's up to date
        FileSystemUtil.writeStringToFile("hello" + System.lineSeparator() + "universe", tagFile);
        long currentLastModified = tagFile.lastModified();
        boolean lastModifiedUpdated = tagFile.setLastModified(currentLastModified + 1000L); // add 1 second
        assertTrue(lastModifiedUpdated);
        boolean result = tagIndex.IsIndexedAndUpToDate(imageFile, tagFile);

        // THEN it should update the entry and return true
        assertTrue(result);
    }

    @Test
    public void testContainsAll_withMatchingEntry_shouldReturnTrue() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world, test");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains all specific tags
        TagList searchTags = TagList.of("hello, world");
        boolean result = tagIndex.containsAll(imageFile, searchTags);

        // THEN it should return true
        assertTrue(result);
    }

    @Test
    public void testContainsAll_withPartialMatch_shouldReturnFalse() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains tags it doesn't have
        TagList searchTags = TagList.of("hello, notThere");
        boolean result = tagIndex.containsAll(imageFile, searchTags);

        // THEN it should return false
        assertFalse(result);
    }

    @Test
    public void testGetMostFrequentTags_withNoEntries_shouldReturnEmptyList() {
        // GIVEN an empty index
        tagIndex.clear();
        
        // WHEN we get the most frequent tags
        List<String> result = tagIndex.getMostFrequentTags(10, null);

        // THEN it should return an empty list
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetMostFrequentTags_withMultipleEntries_shouldReturnSortedByFrequency() throws IOException {
        // GIVEN multiple entries with overlapping tags
        File image1 = new File(tempDir.toFile(), "image1.jpg");
        File tag1 = createTestTagFile("tag1.ice", "hello, world");
        tagIndex.addOrUpdateEntry(image1, tag1);

        File image2 = new File(tempDir.toFile(), "image2.jpg");
        File tag2 = createTestTagFile("tag2.ice", "hello, test");
        tagIndex.addOrUpdateEntry(image2, tag2);

        File image3 = new File(tempDir.toFile(), "image3.jpg");
        File tag3 = createTestTagFile("tag3.ice", "hello, foo");
        tagIndex.addOrUpdateEntry(image3, tag3);

        // WHEN we get the most frequent tags
        List<String> result = tagIndex.getMostFrequentTags(4, null);

        // THEN "hello" should be first (appears 3 times)
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals("hello", result.get(0));
        // The other tags appear once each, so they can be in any order
        assertTrue(result.contains("world"));
        assertTrue(result.contains("test"));
        assertTrue(result.contains("foo"));
    }

    @Test
    public void testGetMostFrequentTags_withExclusionList_shouldExcludeSpecifiedTags() throws IOException {
        // GIVEN multiple entries with tags
        File image1 = new File(tempDir.toFile(), "image1.jpg");
        File tag1 = createTestTagFile("tag1.ice", "hello, world");
        tagIndex.addOrUpdateEntry(image1, tag1);

        File image2 = new File(tempDir.toFile(), "image2.jpg");
        File tag2 = createTestTagFile("tag2.ice", "hello, test");
        tagIndex.addOrUpdateEntry(image2, tag2);

        // WHEN we get the most frequent tags excluding "hello"
        List<String> excludeTags = List.of("hello");
        List<String> result = tagIndex.getMostFrequentTags(10, excludeTags);

        // THEN "hello" should not be in the results
        assertNotNull(result);
        assertFalse(result.contains("hello"));
        assertTrue(result.contains("world"));
        assertTrue(result.contains("test"));
    }

    @Test
    public void testGetMostFrequentTags_withLimitSmallerThanUniqueTagCount_shouldReturnLimitedResults() throws IOException {
        // GIVEN multiple entries with different tags
        File image1 = new File(tempDir.toFile(), "image1.jpg");
        File tag1 = createTestTagFile("tag1.ice", "tag1, tag2, tag3");
        tagIndex.addOrUpdateEntry(image1, tag1);

        File image2 = new File(tempDir.toFile(), "image2.jpg");
        File tag2 = createTestTagFile("tag2.ice", "tag4, tag5");
        tagIndex.addOrUpdateEntry(image2, tag2);

        // WHEN we get the most frequent tags with a limit of 2
        List<String> result = tagIndex.getMostFrequentTags(2, null);

        // THEN we should get exactly 2 tags
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testScan_shouldReturnScanThread() {
        // GIVEN a directory to scan
        File dir = tempDir.toFile();

        // WHEN we initiate a scan
        // THEN it should return a ScanThread (not null)
        assertNotNull(tagIndex.scan(dir, false));
        assertNotNull(tagIndex.scan(dir, true));
    }

    @Test
    public void testLoad_withNonExistentFile_shouldNotThrow() {
        // GIVEN an index with no file
        tagIndex.clear();
        
        // WHEN we try to load
        // THEN it should not throw (just log that file doesn't exist)
        assertDoesNotThrow(() -> tagIndex.load());
    }

    @Test
    public void testSave_shouldPersistEntries() throws IOException {
        // GIVEN an index with entries
        File image1 = new File(tempDir.toFile(), "image1.jpg");
        File tag1 = createTestTagFile("tag1.ice", "hello, world");
        tagIndex.addOrUpdateEntry(image1, tag1);

        // WHEN we save
        tagIndex.save();

        // THEN the file should exist and have content
        long fileSize = tagIndex.fileSize();
        assertTrue(fileSize > 0, "Tag index file should have been created with content");
    }

    @Test
    public void testContainsAll_withNonExistentEntry_shouldReturnFalse() {
        // GIVEN a non-existent entry
        File imageFile = new File(tempDir.toFile(), "nonExistent.jpg");

        // WHEN we check if it contains tags
        TagList searchTags = TagList.of("hello");
        boolean result = tagIndex.containsAll(imageFile, searchTags);

        // THEN it should return false
        assertFalse(result);
    }

    @Test
    public void testContainsAny_withPartialMatch_shouldReturnTrue() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains any of the search tags
        TagList searchTags = TagList.of("hello, notThere");
        boolean result = tagIndex.containsAny(imageFile, searchTags);

        // THEN it should return true (has "hello")
        assertTrue(result);
    }

    @Test
    public void testContainsAny_withNoMatch_shouldReturnFalse() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains any tags it doesn't have
        TagList searchTags = TagList.of("foo, bar");
        boolean result = tagIndex.containsAny(imageFile, searchTags);

        // THEN it should return false
        assertFalse(result);
    }

    @Test
    public void testContainsAny_withNonExistentEntry_shouldReturnFalse() {
        // GIVEN a non-existent entry
        File imageFile = new File(tempDir.toFile(), "nonExistent.jpg");

        // WHEN we check if it contains any tags
        TagList searchTags = TagList.of("hello");
        boolean result = tagIndex.containsAny(imageFile, searchTags);

        // THEN it should return false
        assertFalse(result);
    }

    @Test
    public void testContainsNone_withNoMatch_shouldReturnTrue() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains none of the search tags
        TagList searchTags = TagList.of("foo, bar");
        boolean result = tagIndex.containsNone(imageFile, searchTags);

        // THEN it should return true (has none of them)
        assertTrue(result);
    }

    @Test
    public void testContainsNone_withPartialMatch_shouldReturnFalse() throws IOException {
        // GIVEN an indexed entry with tags
        File imageFile = new File(tempDir.toFile(), "image.jpg");
        File tagFile = createTestTagFile("tag.ice", "hello, world");
        tagIndex.addOrUpdateEntry(imageFile, tagFile);

        // WHEN we check if it contains none of tags it does have
        TagList searchTags = TagList.of("hello, notThere");
        boolean result = tagIndex.containsNone(imageFile, searchTags);

        // THEN it should return false (has "hello")
        assertFalse(result);
    }

    @Test
    public void testContainsNone_withNonExistentEntry_shouldReturnFalse() {
        // GIVEN a non-existent entry
        File imageFile = new File(tempDir.toFile(), "nonExistent.jpg");

        // WHEN we check if it contains none of the tags
        TagList searchTags = TagList.of("hello");
        boolean result = tagIndex.containsNone(imageFile, searchTags);

        // THEN it should return false (entry doesn't exist)
        assertFalse(result);
    }

    // Helper method to create a test tag file
    // Note: TagList.fromFile() reads one tag per line, not comma-separated
    private File createTestTagFile(String filename, String content) throws IOException {
        File tagFile = new File(tempDir.toFile(), filename);
        // Convert comma-separated tags to line-separated format
        String[] tags = content.split(",\\s*");
        StringBuilder fileContent = new StringBuilder();
        for (String tag : tags) {
            if (!tag.trim().isEmpty()) {
                fileContent.append(tag.trim()).append("\n");
            }
        }
        FileSystemUtil.writeStringToFile(fileContent.toString(), tagFile);
        return tagFile;
    }
}
