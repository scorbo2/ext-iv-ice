package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.actions.TagSingleImageAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.UIReloadable;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;

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
public class TagPreviewPanel extends JPanel implements UIReloadable {
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
        textField.setHelpText(getHelpText());
        textField.getTextArea().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
                if (! currentImage.isEmpty()) {
                    TagSingleImageAction.getInstance().actionPerformed(null);
                }
            }
        });
        formPanel.add(textField);
        add(formPanel, BorderLayout.CENTER);
        ReloadUIAction.getInstance().registerReloadable(this);
    }

    /**
     * Returns helpful text showing the currently-configured keyboard shortcut for the tag dialog.
     */
    private String getHelpText() {
        return "Read-only display. Use the tag dialog to edit tags.";

        // It sure would be nice to show the actual keystroke here!
        // But something strange is happening and it's way too late at night to debug it.
        // The code is getting executed in a wonky order, such that the code below
        // is somehow always one out of date. That is, on startup, it's fine, but
        // remap the shortcut and it still shows the old value. Then, remap it again,
        // and it shows the new value from the first remap, but not the current one, and so on.
        // NO IDEA why, so leaving it for now.
        /*
        KeyStroke keystroke = TagSingleImageAction.getInstance().getAcceleratorKey();
        System.out.println("The action has a keystroke of: " + keystroke);
        if (keystroke != null) {
            return "Read-only display. Press " + KeyStrokeManager.keyStrokeToString(keystroke) + " to edit tags.";
        }
        else {
            // Our TagPreviewPanel loads before our accelerator gets set, so peek() the value of it,
            // to determine if this null is an actual null or a false alarm null.
            String keystrokeStr = AppConfig.peek(Version.APP_CONFIG_FILE, "Keystrokes.ICE.quickTagPanel.keyStroke");
            System.out.println("Peeked keystroke: " + keystrokeStr);
            if (keystrokeStr != null && KeyStrokeManager.parseKeyStroke(keystrokeStr) != null) {
                return "Read-only display. Press " + keystrokeStr + " to edit tags.";
            }

            // Oh, I guess it actually is null:
            return "Read-only display. Use the tag dialog to edit tags."; // But you have to bring it up yourself!
        }
        */
    }

    public void clearTags() {
        textField.setText("");
    }

    public void setTagList(TagList tagList) {
        textField.setText(tagList.toString());
    }

    @Override
    public void reloadUI() {
        // Our keyboard shortcut may have changed, so update the help text:
        textField.setHelpText(getHelpText());
    }
}
