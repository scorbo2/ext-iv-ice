package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagDialog;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles mouse clicks on our companion file labels, launching the CompanionFileDialog
 * to view the companion file in question.
 *
 * @author scorbo2
 */
public class CompanionFileMouseListener extends MouseAdapter {

    private static final Logger logger = Logger.getLogger(CompanionFileMouseListener.class.getName());
    private File file;

    /**
     * Creates a CompanionFileMouseListener that will link to the given File.
     * If the File is renamed or moved at runtime, you can call setFile() to
     * update this listener with the new value.
     *
     * @param file The File which will be displayed when this listener gets a mouse event.
     */
    public CompanionFileMouseListener(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        redispatchToParent(e);
        showTagFile(file);
    }

    private void showTagFile(File file) {
        if (file == null) {
            logger.log(Level.SEVERE, "IceExtension: no tag file was provided.");
            return;
        }
        if (!file.exists()) {
            logger.log(Level.WARNING, "The specified file seems to no longer exist: {0}", file.getAbsolutePath());
            return;
        }
        new TagDialog("Image tags", TagList.fromFile(file)).setVisible(true);
    }

    private void redispatchToParent(MouseEvent e) {
        Component source = (Component)e.getSource();
        MouseEvent parentEvent = SwingUtilities.convertMouseEvent(source, e, source.getParent());
        source.getParent().dispatchEvent(parentEvent);
    }

}
