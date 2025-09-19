package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.extensions.ice.TagList;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Represents a read-only panel for displaying tags for the current image.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagPanel extends JPanel {
    private LongTextField textField;

    public TagPanel() {
        setLayout(new BorderLayout());
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(6);
        textField = LongTextField.ofDynamicSizingMultiLine("", 1);
        textField.setMargins(new Margins(24, 4, 24, 4, 4));
        textField.setEnabled(false);
        textField.setHelpText("Read-only display. Press Ctrl+G to edit tags.");
        formPanel.add(textField);
        add(formPanel, BorderLayout.CENTER);
    }

    public void clearTags() {
        textField.setText("");
    }

    public void setTagList(TagList tagList) {
        textField.setText(tagList.toString());
    }
}
