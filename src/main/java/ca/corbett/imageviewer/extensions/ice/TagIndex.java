package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ice.threads.ScanThread;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * If enabled, we'll manage an index file storing all known tags for all known images, along
 * with a lastModified timestamp for the tag file to very quickly determine if reload is needed.
 * SearchThread will be updated to use TagIndex if its enabled.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagIndex {

    private static final Logger log = Logger.getLogger(TagIndex.class.getName());

    public static final String PROP_NAME = "ICE.General.enableTagIndex";

    /**
     * Possible return codes for addOrUpdate() method.
     */
    public enum EntryAddResult {
        ExistingEntryUpdated,
        NewEntryCreated,
        SkippedBecauseUpToDate,
        SkippedBecauseDisabled
    }

    private static TagIndex instance;
    private final File indexFile;
    private final Map<String, TagIndexEntry> indexEntries;

    protected TagIndex() {
        indexFile = new File(Version.SETTINGS_DIR, "tagIndex.ice");
        indexEntries = new HashMap<>();
    }

    public static TagIndex getInstance() {
        if (instance == null) {
            instance = new TagIndex();
        }
        return instance;
    }

    public static boolean isEnabled() {
        return ((BooleanProperty)AppConfig.getInstance().getPropertiesManager().getProperty(PROP_NAME)).getValue();
    }

    public EntryAddResult addOrUpdateEntry(File imageFile, File tagFile) {
        // If disabled by configuration, just do nothing:
        if (! isEnabled()) {
            return EntryAddResult.SkippedBecauseDisabled;
        }

        // Is there an existing entry for this image?
        TagIndexEntry existingEntry = indexEntries.get(imageFile.getAbsolutePath());
        if (existingEntry != null) {
            // And has the tag file changed since we last saw it?
            if (existingEntry.getTagFileLastModified() != tagFile.lastModified() ||
                    existingEntry.getTagFileSize() != tagFile.length()) {
                existingEntry.setTagFileLastModified(tagFile.lastModified());
                existingEntry.setTagFileSize(tagFile.length());
                existingEntry.setTagList(TagList.fromFile(tagFile));
                return EntryAddResult.ExistingEntryUpdated;
            }
            else {
                return EntryAddResult.SkippedBecauseUpToDate;
            }
        }

        // Otherwise, make an entry for this guy:
        TagIndexEntry newEntry = new TagIndexEntry();
        newEntry.setImageFile(imageFile);
        newEntry.setTagFile(tagFile);
        newEntry.setTagFileLastModified(tagFile.lastModified());
        newEntry.setTagFileSize(tagFile.length());
        newEntry.setTagList(TagList.fromFile(tagFile));
        indexEntries.put(imageFile.getAbsolutePath(), newEntry);
        return EntryAddResult.NewEntryCreated;
    }

    /**
     * Removes the index entry for the given image file, if there is one.
     */
    public void removeEntry(File imageFile) {
        indexEntries.remove(imageFile.getAbsolutePath());
    }

    /**
     * Checks if the given imageFile is present in the index and returns false if not.
     * If present, also checks to ensure that the given tagFile has an up-to-date
     * entry in the TagIndex. If not, updates it.
     */
    public boolean IsIndexedAndUpToDate(File imageFile, File tagFile) {
        TagIndexEntry entry = indexEntries.get(imageFile.getAbsolutePath());
        if (entry == null) {
            return false;
        }
        if (entry.getTagFileSize() != tagFile.length() || entry.getTagFileLastModified() != tagFile.lastModified()) {
            addOrUpdateEntry(imageFile, tagFile);
        }
        return true;
    }

    public boolean containsAll(File imageFile, TagList tags) {
        TagIndexEntry indexEntry = indexEntries.get(imageFile.getAbsolutePath());
        return indexEntry != null && indexEntry.containsAll(tags);
    }

    public boolean containsAny(File imageFile, TagList tags) {
        TagIndexEntry indexEntry = indexEntries.get(imageFile.getAbsolutePath());
        return indexEntry != null && indexEntry.containsAny(tags);
    }

    public boolean containsNone(File imageFile, TagList tags) {
        TagIndexEntry indexEntry = indexEntries.get(imageFile.getAbsolutePath());
        return indexEntry != null && indexEntry.containsNone(tags);
    }

    /**
     * Scans the given directory (with optional recursion) looking for any ice tag files,
     * and updates/inserts entries in our tag index in memory as needed.
     */
    public ScanThread scan(File dir, boolean isRecursive) {
        return new ScanThread(dir, isRecursive);
    }

    public void clear() {
        indexEntries.clear();
    }

    public void load() {
        // Hmm, I think the load should happen even if the tag index is disabled.
        // I mean, if it exists, let's load it up so it's ready to go if indexing is later enabled.
        //if (! isEnabled()) {
        //    log.info("IceExtension: tag index is disabled. You can enable it in application settings for increased search performance.");
        //    return;
        //}

        if (! indexFile.exists()) {
            log.info("IceExtension: tag index file not found.");
            return;
        }

        try {
            List<String> lines = FileUtils.readLines(indexFile, (String)null);
            clear(); // after we read the file but before we start processing it
            for (String line : lines) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue; // skip blank lines and comments
                }
                TagIndexEntry indexEntry = new TagIndexEntry(line);
                indexEntries.put(indexEntry.getImageFilePath(), indexEntry);
            }
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "TagIndex: problem reading tag index: "+ioe.getMessage(), ioe);
        }
    }

    public void save() {
        if (! isEnabled()) {
            return;
        }

        // Sort the entries by image file path:
        List<TagIndexEntry> sortedList = indexEntries.entrySet().stream()
                                              .sorted(Map.Entry.comparingByKey())
                                              .map(Map.Entry::getValue)
                                              .toList();

        List<String> lines = new ArrayList<>(sortedList.size());
        for (TagIndexEntry entry : sortedList) {
            lines.add(entry.toString());
        }
        try {
            FileUtils.writeLines(indexFile, lines);
            log.log(Level.INFO, "IceExtension: saved "+lines.size() + " entries to tag index.");
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "TagIndex: problem writing tag index: "+ioe.getMessage(), ioe);
        }
    }

}
