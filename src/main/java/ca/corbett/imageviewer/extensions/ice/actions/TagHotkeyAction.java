package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FilenameUtils;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Logger;

/**
 * An action that can be invoked when a hotkey is pressed to
 * add a predefined list of tags to the currently selected image.
 * <p>
 * Unlike the quick tags panel, this is not a toggle! The tag(s)
 * are added to the image's existing tag list only if they are
 * not already present. Otherwise, there is no change.
 * </p>
 * <p>
 * The coordinating class is TagHotkeyProperty. You generally
 * shouldn't need to instantiate this class directly. Just create
 * a TagHotkeyProperty and treat it more or less as you would
 * treat any other KeyStrokeProperty.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class TagHotkeyAction extends EnhancedAction {

    private static final Logger log = Logger.getLogger(TagHotkeyAction.class.getName());
    private final TagList tagList;

    /**
     * Creates a TagHotkeyAction with the given name.
     */
    public TagHotkeyAction(String name) {
        super(name);
        tagList = new TagList();
    }

    /**
     * Sets the tag list to be associated with this hotkey action.
     * Null or empty input is fine - that will blank out the tag list.
     * If this action is invoked with an empty tag list, it will do nothing.
     */
    public TagHotkeyAction setTagList(TagList tagList) {
        this.tagList.clear();
        this.tagList.addAll(tagList == null ? new TagList() : tagList);
        return this;
    }

    /**
     * Returns the tag list associated with this hotkey action.
     * May be empty.
     */
    public TagList getTagList() {
        return tagList;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // If we have no tag list, do nothing:
        if (tagList.isEmpty()) {
            log.warning(getName()
                                + " no TagList is set for this hotkey. You can configure this in application settings.");
            return;
        }

        // Make sure we have a selected image:
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(getName(), "Nothing selected.");
            return;
        }

        // Figure out where this tag file should live:
        // Note: it's not an error if this file does not yet exist...
        File file = new File(currentImage.getImageFile().getParentFile(),
                             FilenameUtils.getBaseName(currentImage.getImageFile().getName()) + ".ice");
        TagList savedList = TagList.fromFile(file); // Will be empty if file does not exist

        // Add our tags to the saved list and save it:
        savedList.addAll(tagList); // idempotent! Does nothing if they're already there, which is fine.
        savedList.save();

        // Update the tag index and then re-select the current image to refresh it:
        TagIndex.getInstance().addOrUpdateEntry(currentImage.getImageFile(), savedList.getPersistenceFile());
        ImageViewerExtensionManager.getInstance().imageSelected(currentImage);
    }
}
