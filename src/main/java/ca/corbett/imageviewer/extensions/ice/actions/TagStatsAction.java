package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagStatsDialog;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

public class TagStatsAction extends AbstractAction {

    public TagStatsAction() {
        super("Tag index statistics...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new TagStatsDialog().setVisible(true);
    }
}
