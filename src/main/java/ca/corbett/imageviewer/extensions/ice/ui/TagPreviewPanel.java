package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.actions.TagSingleImageAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Represents a read-only panel for displaying tags for the current image.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagPreviewPanel extends JPanel {
    private final LongTextField textField;

    public TagPreviewPanel() {
        setLayout(new BorderLayout());
        FormPanel formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(6);
        textField = LongTextField.ofDynamicSizingMultiLine("", 1);
        textField.setMargins(new Margins(10, 4, 10, 4, 0));
        textField.setEnabled(false);
        this.setBackground(Color.GREEN);
        textField.getTextArea().setColumns(10); // dumb! remove once swing-extras #119 fix is available in 2.5 release
        textField.setHelpText("Read-only display. Press Ctrl+G to edit tags.");
        textField.getTextArea().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
                if (! currentImage.isEmpty()) {
                    new TagSingleImageAction().actionPerformed(null);
                }
            }
        });
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
