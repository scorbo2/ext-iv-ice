package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

public class TagDialog extends JDialog {

    private TagList tagList;
    private LongTextField textField;

    public TagDialog(String title, TagList tagList) {
        super(MainWindow.getInstance(), title, true);
        setSize(new Dimension(500, 220));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.tagList = tagList;
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildFormPanel() {
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(0);
        textField = LongTextField.ofDynamicSizingMultiLine("Tags:", 6);
        textField.setText(tagList.toString());
        textField.setMargins(new Margins(16, 20, 0, 0, 4));
        textField.setHelpText("Enter tags as a comma-separated list.");
        formPanel.add(textField);
        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("Save");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(e -> {
            TagList newList = TagList.of(textField.getText());
            newList.setPersistenceFile(tagList.getPersistenceFile());
            newList.addAll(tagList.getTags());
            tagList = newList;
            tagList.save();
            dispose();
        });
        buttonPanel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(e -> {
            dispose();
        });
        buttonPanel.add(button);

        buttonPanel.setBorder(BorderFactory.createRaisedBevelBorder());
        return buttonPanel;
    }
}
