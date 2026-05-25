package ca.corbett.imageviewer.extensions.ice.io;

import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndexEntry;
import ca.corbett.imageviewer.extensions.ice.TagList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p>
 * In its current form, persistence is backed by SQLite. Legacy text-file loading
 * methods are retained only for migration purposes.
 * </p>
 * <p>
 *     <b>BRIEF PERSISTENCE VERSION HISTORY</b>
 * </p>
 * <ul>
 *     <li><b>v2.2.0</b>: initial implementation using a custom pipe-separated text format.
 *     This format was simple but inefficient, as it repeated the parent directory path for every entry.</li>
 *     <li><b>v2.2.1</b>: upgraded the pipe-separated format to be much more disk-space efficient.
 *     Also added a header line to report the version of the format, to handle compatibility.</li>
 *     <li><b>v3.5.0</b>: deprecated the pipe-separated format and added support for SQLite. The legacy loading/saving
 *     methods are retained for migration purposes, but they are no longer used by the main code path.
 *     The main load/save methods now use SQLite exclusively. The SQLite approach allows much more efficient
 *     storage and faster load/save times, especially for large indexes.</li>
 * </ul>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ICE 2.2.1
 */
public class TagIndexPersistence {

    private static final Logger log = Logger.getLogger(TagIndexPersistence.class.getName());

    private static final String HEADER = "ICE_tag_index";

    // SQLite schema
    private static final String SCHEMA_CREATE_TAG_INDEX =
            "CREATE TABLE IF NOT EXISTS tag_index (" +
                    "image_path TEXT PRIMARY KEY, " +
                    "tag_file_path TEXT NOT NULL, " +
                    "tag_file_size INTEGER NOT NULL, " +
                    "tag_file_last_modified INTEGER NOT NULL, " +
                    "tags TEXT NOT NULL)";

    private static final String SCHEMA_CREATE_METADATA =
            "CREATE TABLE IF NOT EXISTS metadata (" +
                    "schema_version TEXT PRIMARY KEY NOT NULL)";

    private static final String SCHEMA_VERSION_INSERT =
            "INSERT INTO metadata (schema_version) VALUES ('3.5') " +
                    "ON CONFLICT(schema_version) DO UPDATE SET schema_version = '3.5'";

    // Prepared statements for tag_index operations
    private static final String SQL_INSERT_ENTRY =
            "INSERT INTO tag_index (image_path, tag_file_path, tag_file_size, tag_file_last_modified, tags) " +
                    "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_DELETE_ALL = "DELETE FROM tag_index";

    private static final String SQL_LOAD_ALL =
            "SELECT image_path, tag_file_path, tag_file_size, tag_file_last_modified, tags FROM tag_index";

    // Connection pool / singleton
    private static Connection connection;

    /**
     * Returns the active SQLite connection, creating one if necessary.
     * Uses WAL mode for better concurrency characteristics.
     * If the requested DB file differs from the current connection, the old connection is closed first.
     */
    private static Connection getDbConnection(File dbFile) throws SQLException {
        String newUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        if (connection != null && !connection.isClosed()) {
            String currentUrl = connection.getMetaData().getURL();
            if (newUrl.equals(currentUrl)) {
                return connection;
            }
            // Different DB file - close old connection
            connection.close();
            connection = null;
        }
        connection = DriverManager.getConnection(newUrl);
        // Enable WAL mode for better concurrent read/write characteristics
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    /**
     * Closes the active SQLite connection. Used for cleanup (e.g., tests).
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            }
            catch (SQLException e) {
                log.log(Level.WARNING, "Failed to close SQLite connection", e);
            }
            connection = null;
        }
    }

    /**
     * Initializes the SQLite database schema.
     */
    private static void initSchema(File dbFile) throws SQLException {
        Connection conn = getDbConnection(dbFile);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(SCHEMA_CREATE_TAG_INDEX);
            stmt.execute(SCHEMA_CREATE_METADATA);
        }
        // Insert schema version if not already present
        try (PreparedStatement ps = conn.prepareStatement(SCHEMA_VERSION_INSERT)) {
            ps.executeUpdate();
        }
    }

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
     * Saves the given list of TagIndexEntry instances to the given SQLite tag index file.
     * Uses a single transaction: DELETE all existing entries, then bulk INSERT new entries.
     */
    public static void save(List<TagIndexEntry> indexEntries, File dbFile) throws IOException {
        try {
            initSchema(dbFile);
            Connection conn = getDbConnection(dbFile);

            try {
                conn.setAutoCommit(false);
                try (PreparedStatement deletePs = conn.prepareStatement(SQL_DELETE_ALL);
                     PreparedStatement insertPs = conn.prepareStatement(SQL_INSERT_ENTRY)) {

                    // Clear existing data
                    deletePs.executeUpdate();

                    // Bulk insert new entries
                    for (TagIndexEntry entry : indexEntries) {
                        insertPs.setString(1, entry.getImageFile().getAbsolutePath());
                        insertPs.setString(2, entry.getTagFile().getAbsolutePath());
                        insertPs.setLong(3, entry.getTagFileSize());
                        insertPs.setLong(4, entry.getTagFileLastModified());
                        insertPs.setString(5, entry.getTagList().toString());
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
                conn.commit();
            }
            catch (SQLException e) {
                conn.rollback();
                throw new IOException("Failed to save tag index to SQLite: " + e.getMessage(), e);
            }
            finally {
                conn.setAutoCommit(true);
            }
        }
        catch (SQLException e) {
            throw new IOException("Failed to initialize SQLite schema: " + e.getMessage(), e);
        }
    }

    /**
     * Loads all entries from the SQLite database at the given path.
     */
    public static List<TagIndexEntry> loadSqlite(File dbFile) throws IOException {
        List<TagIndexEntry> entries = new ArrayList<>();
        try {
            initSchema(dbFile);
            Connection conn = getDbConnection(dbFile);
            try (PreparedStatement ps = conn.prepareStatement(SQL_LOAD_ALL);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    TagIndexEntry entry = new TagIndexEntry();

                    String imagePath = rs.getString("image_path");
                    String tagFilePath = rs.getString("tag_file_path");
                    long tagFileSize = rs.getLong("tag_file_size");
                    long tagFileLastModified = rs.getLong("tag_file_last_modified");
                    String tagsStr = rs.getString("tags");

                    entry.setImageFile(new File(imagePath));
                    entry.setTagFile(new File(tagFilePath));
                    entry.setTagFileSize(tagFileSize);
                    entry.setTagFileLastModified(tagFileLastModified);
                    entry.setTagList(TagList.of(tagsStr));

                    entries.add(entry);
                }
            }
        }
        catch (SQLException e) {
            throw new IOException("Failed to load tag index from SQLite: " + e.getMessage(), e);
        }
        return entries;
    }

    /**
     * Saves the given list of TagIndexEntry instances to the given tag index file
     * (legacy pipe-separated format).
     */
    public static void saveLegacy(List<TagIndexEntry> indexEntries, File tagIndexFile) throws IOException {
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

        // Save it in one shot:
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
     * <p>
     *     As of v3.5, the name of this method is wrong, because the pipe-separated format is
     *     now officially a legacy format. But, we'll keep these legacy methods for a release or two,
     *     until the migration effort is done, and then they can just be deleted.
     * </p>
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
