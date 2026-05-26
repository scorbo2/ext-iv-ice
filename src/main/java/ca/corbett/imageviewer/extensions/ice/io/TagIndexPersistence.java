package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;
import ca.corbett.imageviewer.extensions.ice.TagList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.swing.text.html.HTML;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Handles saving and loading of the TagIndex to keep this logic in once place and
 * to keep the rest of the code isolated from the details of how the index is stored.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ICE 2.2.1
 */
public class TagIndexPersistence {

    private static final Logger log = Logger.getLogger(TagIndexPersistence.class.getName());

    private static final String HEADER = "ICE_tag_index";

    /**
     * Reports what version of the extension was used to create the given tag index file.
     * We can use this for backwards compatibility if the format changes over time
     * (which it already has between 2.2.0 and 2.2.1).
     *
     * @param tagIndexFile The tag index file in question.
     * @return A String version which will be the ICE extension version which created this file.
     */
    public static String getIndexVersion(File tagIndexFile) throws IOException {
        // Grab the first line in the file:
        String firstLine;
        try (Stream<String> lines = Files.lines(tagIndexFile.toPath(), StandardCharsets.UTF_8)) {
            firstLine = lines.findFirst().orElse("");
        }

        // If the first line contains our header, then parse the version out of it:
        //    example value: ICE_tag_index|2.2.1
        String[] parts = firstLine.split("\\|");
        if (parts.length == 2 && HEADER.equals(parts[0].trim())) {
            return parts[1].trim();
        }

        // If the first line is not our header, then it must be v2.2.0 because we had no header in 2.2.0:
        return "2.2.0";
    }

    /**
     * Saves the given list of TagIndexEntry instances to the given tag index file.
     */
    public static void save(List<TagIndexEntry> indexEntries, File tagIndexFile) throws IOException {
        List<String> lines = new ArrayList<>(1000);
        lines.add(HEADER + "|" + IceExtension.extInfo.getVersion());

        // Build up our list of unique locations:
        Set<String> uniqueLocations = new LinkedHashSet<>(1000);
        for (TagIndexEntry entry : indexEntries) {
            uniqueLocations.add(entry.getImageFile().getParentFile().getAbsolutePath());
        }

        // Write out our unique location list and build out a map of location to identifier:
        int locationIdentifier = 0;
        Map<String, Integer> locationMap = new HashMap<>();
        for (String location : uniqueLocations) {
            lines.add("LOC|"+locationIdentifier+"|"+location);
            locationMap.put(location, locationIdentifier);
            locationIdentifier++;
        }

        // Now write out our entries:
        for (TagIndexEntry entry : indexEntries) {
            int locationId = locationMap.get(entry.getImageFile().getParentFile().getAbsolutePath());
            String line = locationId +
                    "|" +
                    entry.getImageFile().getName() +
                    "|" +
                    entry.getTagFileSize() +
                    "|" +
                    entry.getTagFileLastModified() +
                    "|" +
                    entry.getTagList().toString();
            lines.add(line);
        }

        // Save it in one shot (... do we REALLY want to build out the whole thing in memory first?):
        FileUtils.writeLines(tagIndexFile, lines);
    }

    /**
     * Detects the index file version and loads appropriately. The return is a list of TagIndexEntry instances
     * that are populated and ready to use.
     */
    public static List<TagIndexEntry> load(File tagIndexFile) throws IOException {
        String indexVersion = getIndexVersion(tagIndexFile);
        if ("2.2.0".equals(indexVersion)) {
            return loadLegacyFormat(tagIndexFile);
        }
        return loadCurrentFormat(tagIndexFile);
    }

    /**
     * Loads the "legacy" index file format. This was used in the 2.2.0 release and was pretty
     * space inefficient. It's supported here for backwards compatibility.
     */
    private static List<TagIndexEntry> loadLegacyFormat(File tagIndexFile) throws IOException {
        List<TagIndexEntry> indexEntries = new ArrayList<>(1000);
        try (Stream<String> lines = Files.lines(tagIndexFile.toPath(), StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank() && ! line.trim().startsWith("#"))
                 .forEach(line -> {
                TagIndexEntry indexEntry = new TagIndexEntry();
                String[] parts = line.split("\\|");
                if (!parts[0].isBlank()) {
                    indexEntry.setImageFile(new File(parts[0].trim()));
                }
                if (parts.length > 1) {
                    indexEntry.setTagFile(new File(parts[1].trim()));
                }
                if (parts.length > 2) {
                    try {
                        indexEntry.setTagFileSize(Long.parseLong(parts[2].trim()));
                    }
                    catch (NumberFormatException ignored) {
                        log.warning("Invalid tag file size ignored: "+parts[2]);
                        indexEntry.setTagFileSize(0L);
                    }
                }
                if (parts.length > 3) {
                    try {
                        indexEntry.setTagFileLastModified(Long.parseLong(parts[3].trim()));
                    }
                    catch (NumberFormatException ignored) {
                        log.warning("Invalid tag file last modified ignored: "+parts[3]);
                        indexEntry.setTagFileLastModified(0L);
                    }
                }
                if (parts.length > 4) {
                    indexEntry.setTagList(TagList.of(parts[4]));
                    indexEntries.add(indexEntry);
                }
            });
        }

        return indexEntries;
    }

    /**
     * Loads a tag index file using the current save format. This format is used as of the 2.2.1
     * release, and is much more efficient with disk space.
     */
    private static List<TagIndexEntry> loadCurrentFormat(File tagIndexFile) throws IOException {
        Map<Integer, String> locationMap = new HashMap<>(1000);
        List<TagIndexEntry> indexEntries = new ArrayList<>(1000);
        try (Stream<String> lines = Files.lines(tagIndexFile.toPath(), StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank() && ! line.trim().startsWith("#") && ! line.startsWith(HEADER))
                 .forEach(line -> {
                String[] parts = line.split("\\|");

                // Location?
                if ("LOC".equals(parts[0].trim()) && parts.length == 3) {
                    try {
                        locationMap.put(Integer.parseInt(parts[1].trim()), parts[2].trim());
                    }
                    catch (NumberFormatException ignored) {
                        log.warning("Ignoring invalid LOC identifier: "+parts[1]);
                    }
                }

                // Entry?
                else if (parts.length == 5) {
                    try {
                        String parentPath = locationMap.get(Integer.parseInt(parts[0].trim()));
                        if (parentPath != null) {
                            TagIndexEntry entry = new TagIndexEntry();
                            String imageFileName = parts[1].trim();
                            entry.setImageFile(new File(parentPath, imageFileName));
                            String tagFileName = FilenameUtils.getBaseName(imageFileName) + ".ice";
                            entry.setTagFile(new File(parentPath, tagFileName));
                            entry.setTagFileSize(Long.parseLong(parts[2].trim()));
                            entry.setTagFileLastModified(Long.parseLong(parts[3].trim()));
                            entry.setTagList(TagList.of(parts[4]));
                            indexEntries.add(entry);
                        }
                        else {
                            log.warning("Entry references a non-existent location: "+parts[0]);
                        }
                    }
                    catch (NumberFormatException ignored) {
                        log.warning("Invalid numeric input on line: \""+line+"\"");
                    }
                }

                // Dunno what it was:
                else {
                    log.warning("Ignoring unparseable line: \""+line+"\"");
                }
            });
        }
        return indexEntries;
    }
}
