package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.llm.AiConnectionManager;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.AutoTagBatchDialog;
import ca.corbett.imageviewer.ui.MainWindow;

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
        File dir = MainWindow.getInstance().getCurrentDirectory();
        if (dir == null) {
            MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
            return;
        }

        // Shouldn't be possible to launch this action in image set mode, because we only
        // add the menu item in file system mode, but let's be sure about it:
        if (MainWindow.getInstance().getBrowseMode() != MainWindow.BrowseMode.FILE_SYSTEM) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Auto-tag: the batch tag feature is only available in file system browse mode.");
            return;
        }

        // Make sure we have good LLM configuration before proceeding:
        AiConnectionManager aiManager = new AiConnectionManager(requestTemplate, taggedPrompt, taglessPrompt);
        if (!aiManager.isFeatureEnabled()) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Auto-tag: the batch tag feature is not available because the LLM connection is not properly configured." +
                                                               "\nVisit the Auto-tag settings page in application properties to set it up.");
            return;
        }

        new AutoTagBatchDialog(MainWindow.getInstance(), dir, aiManager).setVisible(true);
    }
}
