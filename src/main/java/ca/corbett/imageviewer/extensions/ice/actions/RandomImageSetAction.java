package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.RandomImageSetDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * An Action to launch the RandomImageSetDialog, for picking a random
 * set of images from the current directory. Requires filesystem browse mode.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2.4.0
 */
public class RandomImageSetAction extends EnhancedAction {

    private static final String NAME = "Generate random image set...";

    public RandomImageSetAction() {
        super(NAME);
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
                                                       "Random image set generation is only available in file system browse mode.");
            return;
        }

        new RandomImageSetDialog(MainWindow.getInstance(), dir).setVisible(true);
    }
}
