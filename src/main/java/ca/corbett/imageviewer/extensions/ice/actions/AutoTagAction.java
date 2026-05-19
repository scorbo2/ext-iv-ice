package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.TextInputDialog;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.llm.AiConnectionManager;
import ca.corbett.imageviewer.extensions.ice.llm.AiErrorBody;
import ca.corbett.imageviewer.extensions.ice.ui.formfield.TagListValidator;
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
    private static final String NAME = "Auto-tag selected image";

    private static AutoTagAction instance;

    private final String requestTemplate;
    private String taggedPrompt;
    private String taglessPrompt;

    private AutoTagAction(String requestTemplate) {
        super(NAME);
        this.requestTemplate = requestTemplate;
    }

    public static AutoTagAction getInstance(String requestTemplate) {
        if (instance == null) {
            instance = new AutoTagAction(requestTemplate);
        }
        return instance;
    }

    /**
     * Sets the system prompt templates for "tagged" (restricted tag list is specified)
     * and "tagless" (no tag list specified) requests. These templates will be used for all
     * subsequent auto-tag requests until they are changed again.
     */
    public void setSysPrompts(String tagged, String tagless) {
        this.taggedPrompt = tagged;
        this.taglessPrompt = tagless;
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

        // Create a new AiConnectionManager for this request.
        // This will query AppConfig for all the latest LLM connection settings,
        // and validate them before proceeding.
        AiConnectionManager aiManager = new AiConnectionManager(requestTemplate, taggedPrompt, taglessPrompt);

        // Make sure our config is good before proceeding:
        if (!aiManager.isFeatureEnabled()) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Auto-tag is not available because the LLM connection is not properly configured." +
                                                               "\nVisit the Auto-tag settings page in application properties to set it up.");
            return;
        }

        // Now we can proceed with the request, and we will handle the response in the callback handler:
        CallbackHandler handler = new CallbackHandler(imageFile);
        aiManager.requestAutoTag(imageFile, handler, handler);
    }

    /**
     * Invoked when we get a response back from the LLM, whether it's a success or an error.
     */
    private static class CallbackHandler
            implements AiConnectionManager.CompletionCallback, AiConnectionManager.ErrorCallback {
        private final File imageFile;
        private final File tagFile;
        private final TagList originalTags;

        public CallbackHandler(File imageFile) {
            this.imageFile = imageFile;

            // Find the tag file for this image.
            // It's not an error if this file does not exist...
            // We'll just end up creating it if/when we get the results from the LLM.
            this.tagFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName()) + ".ice");
            this.originalTags = TagList.fromFile(tagFile); // might be empty; that's okay
        }

        /**
         * This will be called when we get a successful response from the LLM.
         * The tags parameter will contain the list of tags returned by the LLM, which may be empty.
         * We will need to merge these tags with the existing tags for the image, and then update the UI accordingly.
         */
        @Override
        public void onComplete(TagList tagList) {
            // An "empty" return is one where we got back either a completely empty list,
            // or a list with just the NO_TAG sentinel value.
            boolean isEmpty = tagList.isEmpty()
                    || (tagList.size() == 1 && tagList.getTags().get(0).equals(AiConnectionManager.NO_TAGS));

            // If we get back such a list, let the user know:
            if (isEmpty) {
                SwingUtilities.invokeLater(() -> {
                    MainWindow.getInstance().showMessageDialog(NAME,
                                                               "The LLM had no tag suggestions for this image.");
                });
                return;
            }

            // Show the suggested tags to the user and allow review/edit:
            SwingUtilities.invokeLater(() -> reviewAndAccept(tagList));
        }

        /**
         * This will be called if there was an error sending the request or parsing the response.
         * The error parameter will contain details about what went wrong, which we can display to the user.
         */
        @Override
        public void onError(AiErrorBody error) {
            String errorCode = error.getCode() == AiErrorBody.INTERNAL_ERROR ? "(Internal error)" : error.getCode() + "";

            // The request thread already logged the error, so we can just show a simple error message to the user.
            String msg = "Failed to auto-tag image: "
                    + "\n\nMessage:" + error.getMessage()
                    + "\nCode: " + errorCode
                    + "\nType: " + error.getType();
            SwingUtilities.invokeLater(() -> {
                MainWindow.getInstance().showMessageDialog(NAME, msg);
            });
        }

        /**
         * Make sure this gets invoked on the UI thread!
         * This method shows dialogs and performs UI updates.
         */
        private void reviewAndAccept(TagList suggestedTags) {
            TextInputDialog dialog = new TextInputDialog(MainWindow.getInstance(),
                                                         "Suggested tags", TextInputDialog.InputType.MultiLine);
            dialog.setAllowBlank(false);
            dialog.setConfirmLabel("Add tags");
            dialog.addValidator(new TagListValidator());
            dialog.setPrompt("Suggested tags:");
            dialog.setInitialText(suggestedTags.toString());
            dialog.setVisible(true);

            String modifiedTagStr = dialog.getResult();
            if (modifiedTagStr == null) {
                // User canceled, so do nothing.
                return;
            }

            // Otherwise, add all the new tags to the original tag list:
            TagList modifiedTags = TagList.of(modifiedTagStr);
            originalTags.addAll(modifiedTags); // duplicates are pruned automatically, not a big deal.
            originalTags.save(); // commit changes to disk
            TagIndex.getInstance().addOrUpdateEntry(imageFile, tagFile); // tell the TagIndex of this change

            // Select the already-selected image to force a UI update of the displayed tags:
            ImageViewerExtensionManager.getInstance().imageSelected(MainWindow.getInstance().getSelectedImage());
        }
    }
}
