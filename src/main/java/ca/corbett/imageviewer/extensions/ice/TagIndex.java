package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * TODO If enabled, we'll manage an index file storing all known tags for all known images, along
 * with a lastModified timestamp for the tag file to very quickly determine if reload is needed.
 * SearchThread will be updated to use TagIndex if its enabled.
 *
 * TODO wait, how does the search know if a directory has been indexed
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagIndex {

    private static final Logger log = Logger.getLogger(TagIndex.class.getName());

    public static final String PROP_NAME = "ICE.General.enableTagIndex";

    private static TagIndex instance;
    private final File indexFile;
    private final Map<String, TagIndexEntry> indexEntries;

    protected TagIndex() {
        indexFile = new File(Version.SETTINGS_DIR, "tagIndex.ice"); // todo configurable?
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

    // TODO add entry: needs tag file, image file, and tag list
    // TODO search entries: needs tags to search for and search mode

    /**
     * Scans the given directory (with optional recursion) looking for any ice tag files,
     * and updates/inserts entries in our tag index in memory as needed.
     * TODO this should return a worker thread instead of just doing the scan... list might be huge... HUUUGE
     */
    public void scan(File dir, boolean isRecursive) {
        log.info("IceExtension: scanning "+dir.getAbsolutePath());
        int entriesCreated = 0;
        int entriesUpdated = 0;
        int entriesSkippedBecauseUpToDate = 0;
        List<File> tagFiles = FileSystemUtil.findFiles(dir, isRecursive, "ice");
        for (File tagFile : tagFiles) {
            File imageFile = IceExtension.getMatchingImageFile(tagFile);
            if (imageFile == null) {
                continue;
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
                    entriesUpdated++;
                }
                else {
                    entriesSkippedBecauseUpToDate++;
                }
            }

            // Otherwise, make an entry for this guy:
            else {
                TagIndexEntry newEntry = new TagIndexEntry();
                newEntry.setImageFile(imageFile);
                newEntry.setTagFile(tagFile);
                newEntry.setTagFileLastModified(tagFile.lastModified());
                newEntry.setTagFileSize(tagFile.length());
                newEntry.setTagList(TagList.fromFile(tagFile));
                indexEntries.put(imageFile.getAbsolutePath(), newEntry);
                entriesCreated++;
            }
        }

        log.info("IceExtension: tag scan complete. Entries added: " + entriesCreated
                         + "; entries updated: " + entriesUpdated
                         + "; entries skipped (unchanged): " + entriesSkippedBecauseUpToDate);

        // Auto-save if anything was changed:
        if (entriesCreated > 0 || entriesUpdated > 0) {
            save();
        }
    }

    public void clear() {
        indexEntries.clear();
    }

    public void load() {
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
