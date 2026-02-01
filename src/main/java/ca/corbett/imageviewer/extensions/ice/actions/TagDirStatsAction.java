package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagDirStatsDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * An action to launch the TagDirStatsDialog for viewing tag statistics
 * for the currently selected directory.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagDirStatsAction extends EnhancedAction {
    private static final String NAME = "Tag directory statistics...";

    public TagDirStatsAction() {
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
                    "Tag directory statistics is only available in file system browse mode.");
            return;
        }

        new TagDirStatsDialog(MainWindow.getInstance(), dir).setVisible(true);
    }
}
