package ca.corbett.imageviewer.extensions.ice;

import java.io.File;
import java.util.Objects;

/**
 * Represents a single entry for the tag index - each entry knows about
 * the image file it describes, the tag file that stores the tag information,
 * the list of tags that describe the image, and some metadata about
 * the tag file itself for performance reasons.
 * <p>
 *     <b>Persistence</b> - the tag index is written to disk in a custom
 *     pipe separated file with the following fields:
 * </p>
 * <ol>
 *     <li><b>image file path</b> - the absolute path of the image file
 *     <li><b>tag file path</b> - the absolute path of the tag file
 *     <li><b>tag file size</b> - the size of the tag file in bytes
 *     <li><b>tag file last modified</b> - the last modified timestamp of the tag file as a long value
 *     <li><b>tag list</b> - the tag list in comma-separated form
 * </ol>
 * The tag file size and last modified values are stored here for performance reasons.
 * We can very quickly check these values to see if they have changed since the last time
 * the tag file was scanned. If so, the tag file is re-parsed and stored in the index entry.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagIndexEntry {
    private String imageFilePath;

    private String tagFilePath;
    private File tagFile;

    private String tagListRawString;
    private TagList tagList;

    private long tagFileLastModified;
    private long tagFileSize;

    public TagIndexEntry() {
        tagList = new TagList();
    }

    public TagIndexEntry(String sourceLine) {
        String[] parts = sourceLine.split("\\|");
        if (! parts[0].isBlank()) {
            imageFilePath = parts[0];
        }
        if (parts.length > 1) {
            tagFilePath = parts[1];
            tagFile = new File(tagFilePath);
        }
        if (parts.length > 2) {
            tagFileSize = Long.parseLong(parts[2]);
        }
        if (parts.length > 3) {
            tagFileLastModified = Long.parseLong(parts[3]);
        }
        if (parts.length > 4) {
            tagListRawString = parts[4];
            tagList = TagList.of(tagListRawString);
        }
        else {
            tagList = new TagList();
        }
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    public void setImageFile(File imageFile) {
        imageFilePath = imageFile.getAbsolutePath();
    }

    public void setImageFilePath(String path) {
        imageFilePath = path;
    }

    public String getTagFilePath() {
        return tagFilePath;
    }

    public File getTagFile() {
        return tagFile;
    }

    public void setTagFilePath(String tagFilePath) {
        this.tagFilePath = tagFilePath;
        this.tagFile = new File(tagFilePath);
    }

    public void setTagFile(File tagFile) {
        this.tagFile = tagFile;
        this.tagFilePath = tagFile.getAbsolutePath();
    }

    public TagList getTagList() {
        return tagList;
    }

    public void setTagList(TagList tagList) {
        tagListRawString = tagList.toString();
        this.tagList = TagList.of(tagListRawString); // make our own copy of it for immutability
    }

    public long getTagFileLastModified() {
        return tagFileLastModified;
    }

    public void setTagFileLastModified(long tagFileLastModified) {
        this.tagFileLastModified = tagFileLastModified;
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
        return tagFileLastModified == that.tagFileLastModified
                && tagFileSize == that.tagFileSize
                && Objects.equals(imageFilePath, that.imageFilePath)
                && Objects.equals(tagFilePath, that.tagFilePath)
                && Objects.equals(tagListRawString, that.tagListRawString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageFilePath, tagFilePath, tagListRawString, tagFileLastModified, tagFileSize);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(imageFilePath == null ? "" : imageFilePath);
        sb.append("|");
        sb.append(tagFilePath == null ? "" : tagFilePath);
        sb.append("|");
        sb.append(tagFileSize);
        sb.append("|");
        sb.append(tagFileLastModified);
        sb.append("|");
        sb.append(tagListRawString == null ? "" : tagListRawString);
        return sb.toString();
    }
}
