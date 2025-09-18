package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.JDialog;
import java.awt.Dimension;

public class TagDialog extends JDialog {

    private final TagList tagList;

    public TagDialog(String title, TagList tagList) {
        super(MainWindow.getInstance(), title, true);
        setSize(new Dimension(400, 300));
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.tagList = tagList;
    }
}
