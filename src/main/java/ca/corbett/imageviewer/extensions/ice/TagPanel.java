package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.forms.fields.PanelField;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Represents an input panel where the user can view/edit the tag list for a given image.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagPanel extends JPanel {
    public TagPanel() {
        setLayout(new BorderLayout());
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(6);
        formPanel.add(LongTextField.ofDynamicSizingMultiLine("Tags (comma-separated):", 1)
                              .setMargins(new Margins(24, 4, 4, 4, 4)));
        add(formPanel, BorderLayout.CENTER);

        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(6);
        PanelField panelField = new PanelField(new FlowLayout(FlowLayout.LEFT));
        panelField.setMargins(new Margins(4, 0, 24, 4, 4));
        JButton button = new JButton("Set");
        button.setPreferredSize(new Dimension(70,23));
        panelField.getPanel().add(button);
        formPanel.add(panelField);
        add(formPanel, BorderLayout.EAST);
    }
}
