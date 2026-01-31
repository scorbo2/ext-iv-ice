package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagDialog;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FilenameUtils;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * An action to launch the TagDialog for the currently selected image.
 * This one is singleton because it has an associated KeyStrokeProperty, and we want
 * its accelerator to get updated properly if the user remaps the keyboard shortcut.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagSingleImageAction extends EnhancedAction {

    private static final String NAME = "Edit image tags";
    private static TagSingleImageAction instance;

    private TagSingleImageAction() {
        super(NAME);
    }

    public static TagSingleImageAction getInstance() {
        if (instance == null) {
            instance = new TagSingleImageAction();
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

        // Not an error if this file does not exist...
        // this will just show an empty dialog and if the user hits save, we'll create it.
        File file = new File(currentImage.getImageFile().getParentFile(),
                             FilenameUtils.getBaseName(currentImage.getImageFile().getName())+".ice");
        new TagDialog("Image tags", currentImage.getImageFile(), TagList.fromFile(file)).setVisible(true);
    }
}
