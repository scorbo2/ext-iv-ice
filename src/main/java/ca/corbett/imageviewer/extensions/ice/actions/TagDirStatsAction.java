package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagDirStatsDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;

public class TagDirStatsAction extends AbstractAction {
    public TagDirStatsAction() {
        super("Tag directory statistics...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File dir = MainWindow.getInstance().getCurrentDirectory();
        if (dir == null) {
            MainWindow.getInstance().showMessageDialog("Tag directory statistics", "Nothing selected.");
            return;
        }

        // Shouldn't be possible to launch this action in image set mode, because we only
        // add the menu item in file system mode, but let's be sure about it:
        if (MainWindow.getInstance().getBrowseMode() != MainWindow.BrowseMode.FILE_SYSTEM) {
            MainWindow.getInstance().showMessageDialog("Tag directory statistics",
                    "Tag directory statistics is only available in file system browse mode.");
            return;
        }

        new TagDirStatsDialog(MainWindow.getInstance(), dir).setVisible(true);
    }
}
