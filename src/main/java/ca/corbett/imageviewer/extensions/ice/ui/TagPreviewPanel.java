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
}
