package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.actions.TagSingleImageAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Represents a read-only panel for displaying tags for the current image.
 * Clicking on the text field will open the tag dialog for the current image.
 * The help label for the text field will show the currently-configured keyboard shortcut
 * for the tag dialog, if there is one.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagPreviewPanel extends JPanel {
    private final LongTextField textField;

    public TagPreviewPanel() {
        setLayout(new BorderLayout());
        FormPanel formPanel = new FormPanel(Alignment.CENTER);
        formPanel.setBorderMargin(4);
        formPanel.setBackground(AppConfig.getInstance().getDefaultBackground());
        textField = LongTextField.ofDynamicSizingMultiLine("", 1);
        textField.setMargins(new Margins(10, 4, 10, 4, 0));
        textField.setEnabled(false);
        textField.setHelpText(getHelpText());
        textField.getTextArea().addMouseListener(new TagFieldMouseListener());
        formPanel.add(textField);
        add(formPanel, BorderLayout.CENTER);
    }

    /**
     * Returns helpful text showing the currently-configured keyboard shortcut for the tag dialog.
     */
    private String getHelpText() {
        KeyStroke ks;

        // Try to get it from AppConfig first:
        // (This is the normal case, after initial startup)
        AbstractProperty prop = AppConfig.getInstance().getPropertiesManager()
                                         .getProperty(IceExtension.imageTagShortcutProp);
        if (prop != null) {
            ks = ((KeyStrokeProperty)prop).getKeyStroke();
        }

        // On initial startup, this class's constructor is invoked before AppConfig queries our properties,
        // so AppConfig may wrongly return null above. In that case, we can try peeking directly
        // into AppConfig's persistent storage. This feels a bit hacky, but it *should* only happen once on startup.
        else {
            String keystrokeStr = AppConfig.peek(Version.APP_CONFIG_FILE,
                                                 IceExtension.imageTagShortcutProp + ".keyStroke");
            // It may legitimately be null here, if the user has unassigned the shortcut:
            // (or if this is a first run on a brand-new install, but that will fix itself on second run)
            // (yeah, this feels hacky too, but oh well)
            ks = keystrokeStr == null ? null : KeyStrokeManager.parseKeyStroke(keystrokeStr);
        }

        // At this point, we either have a valid keystroke, or there isn't one assigned to our action:
        if (ks != null) {
            return "Read-only display. Press " + KeyStrokeManager.keyStrokeToString(ks) + " to edit tags.";
        }
        else {
            return "Read-only display. Use the tag dialog to edit tags."; // But you have to bring it up yourself!
        }
    }

    public void clearTags() {
        textField.setText("");
    }

    public void setTagList(TagList tagList) {
        textField.setText(tagList.toString());
    }

    /**
     * A very simple mouse listener to execute our TagSingleImageAction when the user clicks on the tag preview.
     * This is just a convenient shortcut to open the tag dialog for the current image, since the tag preview is
     * read-only and doesn't have any other interactivity.
     */
    private static class TagFieldMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
            if (!currentImage.isEmpty()) {
                TagSingleImageAction.getInstance().actionPerformed(null);
            }
        }
    }
}
