package ca.corbett.imageviewer.extensions.ice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

public class TagList {
    private static final Logger log = Logger.getLogger(TagList.class.getName());

    private final Set<String> tags = new LinkedHashSet<>();

    public TagList() {

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

    public int size() {
        return tags.size();
    }

    public void clear() {
        tags.clear();
    }

    public void addAll(List<String> tags) {
        for (String tag : tags) {
            add(tag);
        }
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

    @Override
    public String toString() {
        return String.join(", ", tags);
    }
}
