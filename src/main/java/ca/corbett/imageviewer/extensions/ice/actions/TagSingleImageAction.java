package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagDialog;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FilenameUtils;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;

public class TagSingleImageAction extends AbstractAction {

    public TagSingleImageAction() {
        super("Edit image tags");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Edit image tags", "Nothing selected.");
            return;
        }

        // Not an error if this file does not exist...
        // this will just show an empty dialog and if the user hits save, we'll create it.
        File file = new File(currentImage.getImageFile().getParentFile(),
                             FilenameUtils.getBaseName(currentImage.getImageFile().getName())+".ice");
        new TagDialog("Image tags", TagList.fromFile(file)).setVisible(true);
    }
}
