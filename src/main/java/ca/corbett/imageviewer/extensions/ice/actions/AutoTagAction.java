package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.llm.AiConnectionManager;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FilenameUtils;

import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Triggers an auto-tag for the selected image, or does nothing if no image is selected.
 * This feature is experimental and subject to change!
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class AutoTagAction extends EnhancedAction {
    private static final String NAME = "Auto-tag image";

    private static AutoTagAction instance;

    private final String requestTemplate;
    private final String requestTemplateTagless;

    private AutoTagAction(String requestTemplate, String requestTemplateTagless) {
        super(NAME);
        this.requestTemplate = requestTemplate;
        this.requestTemplateTagless = requestTemplateTagless;
    }

    public static AutoTagAction getInstance(String requestTemplate, String requestTemplateTagless) {
        if (instance == null) {
            instance = new AutoTagAction(requestTemplate, requestTemplateTagless);
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
            return;
        }

        final File imageFile = currentImage.getImageFile();
        String filename = imageFile.getName().toLowerCase();
        if (!filename.endsWith(".jpg") && !filename.endsWith(".jpeg") && !filename.endsWith(".png")) {
            MainWindow.getInstance().showMessageDialog(NAME, "Auto-tagging is only supported for JPEG and PNG images.");
            return;
        }

        // Find the tag file for this image.
        // It's not an error if this file does not exist...
        // We'll just end up creating it if/when we get the results from the LLM.
        File tagFile = new File(currentImage.getImageFile().getParentFile(),
                                FilenameUtils.getBaseName(currentImage.getImageFile().getName()) + ".ice");
        TagList originalTags = TagList.fromFile(tagFile); // might be empty; that's okay

        AiConnectionManager aiManager = new AiConnectionManager(requestTemplate, requestTemplateTagless);
        aiManager.requestAutoTag(imageFile, tagList -> {
            // An "empty" return is one where we got back either a completely empty list,
            // or a list with just the NO_TAG sentinel value.
            boolean isEmpty = tagList.isEmpty()
                    || (tagList.size() == 1 && tagList.getTags().get(0).equals(AiConnectionManager.NO_TAGS));

            // Wonky case: if originalTags isn't empty, and we get back an "empty" list, then do nothing.
            if (!originalTags.isEmpty() && isEmpty) {
                return;
            }

            // Otherwise, add all the new tags to the original tag list:
            originalTags.addAll(tagList); // duplicates are pruned automatically, not a big deal.
            originalTags.save(); // commit changes to disk
            TagIndex.getInstance().addOrUpdateEntry(imageFile, tagFile); // tell the TagIndex of this change

            // Select the already-selected image to force a UI update of the displayed tags:
            // (we may or may not be on the UI thread in this callback, so let's be safe with our UI update)
            SwingUtilities.invokeLater(() -> {
                ImageViewerExtensionManager.getInstance().imageSelected(MainWindow.getInstance().getSelectedImage());
            });
        });
    }

    private void onComplete(TagList tagList) {
    }
}
