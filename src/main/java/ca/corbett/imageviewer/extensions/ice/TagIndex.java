package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FileSystem;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private final List<TagIndexEntry> entries;

    protected TagIndex() {
        indexFile = new File(Version.SETTINGS_DIR, "tagIndex.ice");
        entries = new ArrayList<>();
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

    public void clear() {
        entries.clear();
    }

    public void load() {
        if (! indexFile.exists()) {
            return;
        }

        try {
            List<String> lines = FileUtils.readLines(indexFile, (String)null);
            entries.clear();
            for (String line : lines) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue; // skip blank lines and comments
                }
                entries.add(new TagIndexEntry(line));
            }
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "TagIndex: problem reading tag index: "+ioe.getMessage(), ioe);
        }
    }

    public void save() {
        if (indexFile.exists()) {
            indexFile.delete();
        }

        // TODO write it out
    }

    protected static class TagIndexEntry {
        private String imageFilePath;
        private String tagListString;
        private File imageFile;
        private TagList tagList;
        private long lastModified;
        private long tagFileSize;

        public TagIndexEntry() {
            tagList = new TagList();
        }

        public TagIndexEntry(String sourceLine) {
            String[] parts = sourceLine.split("\\|");
            if (! parts[0].isBlank()) {
                imageFilePath = parts[0];
                imageFile = new File(imageFilePath);
            }
            if (parts.length > 1) {
                tagFileSize = Long.parseLong(parts[1]);
            }
            if (parts.length > 2) {
                lastModified = Long.parseLong(parts[2]);
            }
            if (parts.length > 3) {
                tagListString = parts[3];
                tagList = TagList.of(tagListString);
            }
            else {
                tagList = new TagList();
            }
        }

        public String getImageFilePath() {
            return imageFilePath;
        }

        public File getImageFile() {
            return imageFile;
        }

        public void setImageFile(File imageFile) {
            imageFilePath = imageFile.getAbsolutePath();
            this.imageFile = imageFile;
        }

        public void setImageFilePath(String path) {
            imageFilePath = path;
            imageFile = new File(imageFilePath);
        }

        public TagList getTagList() {
            return tagList;
        }

        public void setTagList(TagList tagList) {
            tagListString = tagList.toString();
            this.tagList = TagList.of(tagListString); // make our own copy of it for immutability
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        public long getTagFileSize() {
            return tagFileSize;
        }

        public void setTagFileSize(long tagFileSize) {
            this.tagFileSize = tagFileSize;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TagIndexEntry that)) { return false; }
            return lastModified == that.lastModified
                    && tagFileSize == that.tagFileSize
                    && Objects.equals(imageFilePath, that.imageFilePath)
                    && Objects.equals(tagListString, that.tagListString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(imageFilePath, tagListString, lastModified, tagFileSize);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(imageFilePath == null ? "" : imageFilePath);
            sb.append("|");
            sb.append(tagFileSize);
            sb.append("|");
            sb.append(lastModified);
            sb.append("|");
            sb.append(tagListString == null ? "" : tagListString);
            return sb.toString();
        }
    }
}
