package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagStatsDialog;

import java.awt.event.ActionEvent;

/**
 * An action to launch the TagStatsDialog, for viewing tag index statistics.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagStatsAction extends EnhancedAction {

    private static final String NAME = "Tag index statistics...";

    public TagStatsAction() {
        super(NAME);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new TagStatsDialog().setVisible(true);
    }
}
