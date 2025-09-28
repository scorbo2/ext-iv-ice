package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagImagesDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

public class TagMultipleImagesAction extends AbstractAction {

    public TagMultipleImagesAction(String name) {
        super(name);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.FILE_SYSTEM
            && MainWindow.getInstance().getCurrentDirectory() == null) {
            MainWindow.getInstance().showMessageDialog("Tag images", "No directory selected.");
            return;
        }
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.IMAGE_SET
            && MainWindow.getInstance().getImageSetPanel().getSelectedImageSet().isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Tag images", "No image set selected.");
            return;
        }

        new TagImagesDialog("Tag images").setVisible(true);
    }
}
