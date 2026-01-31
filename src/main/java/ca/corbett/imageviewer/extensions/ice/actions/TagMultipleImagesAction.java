package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagImagesDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;

public class TagMultipleImagesAction extends EnhancedAction {

    private static final String NAME = "Tag images...";

    public TagMultipleImagesAction() {
        this(NAME);
    }

    public TagMultipleImagesAction(String name) {
        super(name);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.FILE_SYSTEM
            && MainWindow.getInstance().getCurrentDirectory() == null) {
            MainWindow.getInstance().showMessageDialog(NAME, "No directory selected.");
            return;
        }
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.IMAGE_SET
            && MainWindow.getInstance().getImageSetPanel().getSelectedImageSet().isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME, "No image set selected.");
            return;
        }

        new TagImagesDialog(NAME).setVisible(true);
    }
}
