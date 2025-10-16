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

    private File imageFile;
    private File tagFile;
    private TagList tagList;
    private long tagFileLastModified;
    private long tagFileSize;

    public TagIndexEntry() {
        tagList = new TagList();
    }

    public File getImageFile() {
        return imageFile;
    }

    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }

    public File getTagFile() {
        return tagFile;
    }
    public void setTagFile(File tagFile) {
        this.tagFile = tagFile;
    }

    public TagList getTagList() {
        return tagList;
    }

    public boolean containsAll(TagList tags) {
        if (tagList == null) {
            return false;
        }
        return tagList.containsAll(tags);
    }

    public boolean containsAny(TagList tags) {
        if (tagList == null) {
            return false;
        }
        return tagList.containsAny(tags);
    }

    public boolean containsNone(TagList tags) {
        if (tagList == null) {
            return false;
        }
        return tagList.containsNone(tags);
    }

    public void setTagList(TagList tagList) {
        this.tagList = TagList.of(tagList.toString()); // make our own copy of it for immutability
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
        String imageFilePath = (imageFile == null) ? "" : imageFile.getAbsolutePath();
        String thatImageFilePath = (that.imageFile == null) ? "" : that.imageFile.getAbsolutePath();
        String tagFilePath = (tagFile == null) ? "" : tagFile.getAbsolutePath();
        String thatTagFilePath = (that.tagFile == null) ? "" : that.tagFile.getAbsolutePath();
        String rawTagList = (tagList == null) ? "" : tagList.toString();
        String thatRawTagList = (that.tagList == null) ? "" : that.tagList.toString();
        return tagFileLastModified == that.tagFileLastModified
                && tagFileSize == that.tagFileSize
                && Objects.equals(imageFilePath, thatImageFilePath)
                && Objects.equals(tagFilePath, thatTagFilePath)
                && Objects.equals(rawTagList, thatRawTagList);
    }

    @Override
    public int hashCode() {
        String imageFilePath = (imageFile == null) ? "" : imageFile.getAbsolutePath();
        String tagFilePath = (tagFile == null) ? "" : tagFile.getAbsolutePath();
        String rawTagList = (tagList == null) ? "" : tagList.toString();
        return Objects.hash(imageFilePath, tagFilePath, rawTagList, tagFileLastModified, tagFileSize);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(imageFile == null ? "" : imageFile.getAbsolutePath());
        sb.append("|");
        sb.append(tagFile == null ? "" : tagFile.getAbsolutePath());
        sb.append("|");
        sb.append(tagFileSize);
        sb.append("|");
        sb.append(tagFileLastModified);
        sb.append("|");
        sb.append(tagList == null ? "" : tagList.toString());
        return sb.toString();
    }
}
