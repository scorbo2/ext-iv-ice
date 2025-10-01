package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.SearchDialog;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;

public class SearchAction extends AbstractAction {

    public SearchAction() {
        super("Search...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new SearchDialog().setVisible(true);
    }
}
