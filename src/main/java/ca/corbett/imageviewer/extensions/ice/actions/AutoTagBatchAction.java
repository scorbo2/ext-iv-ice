package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.llm.AiConnectionManager;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.AutoTagBatchDialog;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.imagesets.ImageSet;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Shows a dialog that allows auto-tagging of all jpeg and/or png images in the selected
 * directory, with optional recursion. This feature is experimental and subject to change!
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class AutoTagBatchAction extends EnhancedAction {
    private static final String NAME = "Auto-tag images...";

    private static AutoTagBatchAction instance;

    private final String requestTemplate;
    private String taggedPrompt;
    private String taglessPrompt;

    private AutoTagBatchAction(String requestTemplate) {
        super(NAME);
        this.requestTemplate = requestTemplate;
    }

    public static AutoTagBatchAction getInstance(String requestTemplate) {
        if (instance == null) {
            instance = new AutoTagBatchAction(requestTemplate);
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
        // We need to have a good LLM configuration before proceeding:
        AiConnectionManager aiManager = new AiConnectionManager(requestTemplate, taggedPrompt, taglessPrompt);
        if (!aiManager.isFeatureEnabled()) {
            String msg = "Auto-tag: the batch tag feature is not available because the LLM connection is not properly configured." +
                    "\nVisit the Auto-tag settings page in application properties to set it up.";
            MainWindow.getInstance().showMessageDialog(NAME, msg);
            return;
        }

        // If we're in filesystem mode, we need a directory to operate on:
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.FILE_SYSTEM) {
            File dir = MainWindow.getInstance().getCurrentDirectory();
            if (dir == null) {
                MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
                return;
            }
            new AutoTagBatchDialog(MainWindow.getInstance(), dir, aiManager).setVisible(true);
        }

        // Otherwise, we need an ImageSet to operate on:
        else {
            ImageSet imageSet = MainWindow.getInstance().getImageSetPanel().getSelectedImageSet().orElse(null);
            if (imageSet == null) {
                MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
                return;
            }
            new AutoTagBatchDialog(MainWindow.getInstance(), imageSet, aiManager).setVisible(true);
        }
    }
}
