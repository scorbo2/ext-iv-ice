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
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.IMAGE_SET) {
            MainWindow.getInstance().showMessageDialog("Tag images", "This feature only works when browsing the file system.");
            return;
        }

        new TagImagesDialog("Tag images", MainWindow.getInstance().getCurrentDirectory()).setVisible(true);
    }
}
