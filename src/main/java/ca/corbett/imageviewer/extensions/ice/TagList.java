package ca.corbett.imageviewer.extensions.ice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagList {
    private static final Logger log = Logger.getLogger(TagList.class.getName());

    private final Set<String> tags = new LinkedHashSet<>();
    private File persistenceFile;

    public TagList() {
        persistenceFile = null;
    }

    public static TagList of(String commaSeparatedInput) {
        TagList list = new TagList();
        if (commaSeparatedInput == null || commaSeparatedInput.isBlank()) {
            return list;
        }

        String[] tags = commaSeparatedInput.split(",");
        for (String tag : tags) {
            list.add(tag);
        }
        return list;
    }

    public static TagList fromFile(File inputFile) {
        TagList tagList = new TagList();
        tagList.setPersistenceFile(inputFile);
        if (! inputFile.exists()) {
            return tagList; // this is not an error... just return an empty list
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line = reader.readLine();
            while (line != null) {
                tagList.add(line);
                line = reader.readLine();
            }
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "IceExtension: problem reading tag list file: "+ioe.getMessage(), ioe);
        }
        return tagList;
    }

    public void setPersistenceFile(File f) {
        this.persistenceFile = f;
    }

    public File getPersistenceFile() {
        return persistenceFile;
    }

    public int size() {
        return tags.size();
    }

    public void clear() {
        tags.clear();
    }

    public boolean containsAll(TagList other) {
        return tags.containsAll(other.tags);
    }

    public boolean containsAny(TagList other) {
        for (String tag : other.getTags()) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsNone(TagList other) {
        return ! containsAny(other);
    }

    public void addAll(List<String> tags) {
        for (String tag : tags) {
            add(tag);
        }
    }

    public void addAll(TagList other) {
        tags.addAll(other.tags);
    }

    public void add(String tag) {
        String strippedTag = stripTag(tag);
        if (strippedTag.isBlank()) {
            log.warning("TagList: Ignoring blank tag.");
            return;
        }
        tags.add(strippedTag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(stripTag(tag));
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    /**
     * Convert to lowercase, remove leading and trailing whitespace, convert null to empty string.
     */
    protected static String stripTag(String tag) {
        if (tag == null) {
            tag = "";
        }
        return tag.toLowerCase().trim();
    }

    public void save() {
        if (persistenceFile == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(persistenceFile))) {
            for (String tag : getTags()) {
                writer.write(tag+System.lineSeparator());
            }
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "IceExtension: problem saving tag file: "+ioe.getMessage(), ioe);
        }
    }

    @Override
    public String toString() {
        return String.join(", ", tags);
    }
}
