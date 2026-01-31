package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.SearchDialog;

import java.awt.event.ActionEvent;

/**
 * An action to launch the SearchDialog, for searching images by tag.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class SearchAction extends EnhancedAction {

    private static final String NAME = "Search...";

    public SearchAction() {
        super(NAME);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new SearchDialog().setVisible(true);
    }
}
