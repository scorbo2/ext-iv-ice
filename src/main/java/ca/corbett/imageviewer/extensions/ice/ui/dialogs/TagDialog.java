package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

public class TagDialog extends JDialog {

    private TagList tagList;
    private LongTextField textField;
    private final File imageFile;

    public TagDialog(String title, File imageFile, TagList tagList) {
        super(MainWindow.getInstance(), title, true);
        setSize(new Dimension(500, 220));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.tagList = tagList;
        this.imageFile = imageFile;
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
        addKeyBindings();
    }

    private JPanel buildFormPanel() {
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(0);
        textField = LongTextField.ofDynamicSizingMultiLine("Tags:", 6);
        textField.setText(tagList.toString());
        textField.setMargins(new Margins(16, 20, 0, 0, 4));
        textField.setHelpText("<html>Enter tags as a comma-separated list.<br>Whitespace is ignored between tags.</html>");
        formPanel.add(textField);
        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("Save");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(e -> handleSave());
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

    private void handleSave() {
        TagList newList = TagList.of(textField.getText());
        newList.setPersistenceFile(tagList.getPersistenceFile());
        //newList.addAll(tagList.getTags()); // wtf? why add the original tags instead of just using the text field?
        tagList = newList;
        tagList.save();
        TagIndex.getInstance().addOrUpdateEntry(imageFile, tagList.getPersistenceFile());
        ImageViewerExtensionManager.getInstance().imageSelected(MainWindow.getInstance().getSelectedImage());
        dispose();
    }

    private void addKeyBindings() {
        // Modify our text area so that "enter" key triggers the dialog save:
        InputMap im = textField.getTextArea().getInputMap(JComponent.WHEN_FOCUSED);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "save");
        ActionMap am = textField.getTextArea().getActionMap();
        am.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSave();
            }
        });

        // Now get the root pane's input map for WHEN_IN_FOCUSED_WINDOW condition
        // This allows the bindings to work in theory regardless of which component has focus
        // (though I note the JTextArea still steals "enter", hence the code block above)
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // Bind Enter key to save handler:
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ok");
        actionMap.put("ok", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSave();
            }
        });

        // Bind ESC key to Cancel action - this works even if focus is in JTextArea,
        // because JTextArea doesn't have any particular built-in handler for the escape key:
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        actionMap.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }
}
