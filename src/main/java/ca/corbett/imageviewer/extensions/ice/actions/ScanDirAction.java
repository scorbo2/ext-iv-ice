package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.threads.ScanThread;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;

public class ScanDirAction extends AbstractAction {

    private final boolean isRecursive;

    public ScanDirAction(String name, boolean isRecursive) {
        super(name);
        this.isRecursive = isRecursive;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File dir = MainWindow.getInstance().getCurrentDirectory();
        if (dir == null) {
            MainWindow.getInstance().showMessageDialog("Scan dir", "Nothing selected.");
            return;
        }

        ScanThread scanThread = TagIndex.getInstance().scan(dir, isRecursive);
        new MultiProgressDialog(MainWindow.getInstance(), "Tag scan").runWorker(scanThread, true);
    }
}
