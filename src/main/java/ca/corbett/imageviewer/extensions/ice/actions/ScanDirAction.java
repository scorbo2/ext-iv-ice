package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.progress.MultiProgressAdapter;
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

        final ScanThread scanThread = TagIndex.getInstance().scan(dir, isRecursive);
        scanThread.addProgressListener(new MultiProgressAdapter() {
            @Override
            public void progressComplete() {
                MainWindow.getInstance().showMessageDialog("Scan complete",
                                                           "Tag scan complete. Entries added: "
                                                                   + scanThread.getEntriesCreated()
                                                                   + "; entries updated: "
                                                                   + scanThread.getEntriesUpdated()
                                                                   + "; entries skipped (unchanged): "
                                                                   + scanThread.getEntriesSkippedBecauseUpToDate());
            }
        });
        new MultiProgressDialog(MainWindow.getInstance(), "Tag scan").runWorker(scanThread, true);
    }
}
