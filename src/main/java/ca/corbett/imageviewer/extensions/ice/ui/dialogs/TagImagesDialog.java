package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressAdapter;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.threads.BatchTagThread;
import ca.corbett.imageviewer.ui.MainWindow;

import static ca.corbett.imageviewer.extensions.ice.threads.BatchTagThread.TaggingOperation;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * For batch-tagging a directory of images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagImagesDialog extends JDialog {

    private static final Logger log = Logger.getLogger(TagImagesDialog.class.getName());

    private static final String[] recurseOptionsFileSystem = {
            "Tag all images in this directory",
            "Tag all images in this directory recursively"
    };
    private static final String[] recurseOptionsImageSet = {
            "Tag all images in this image set"
    };

    private final MainWindow.BrowseMode browseMode;
    private FormPanel formPanel;
    private ComboField<String> recursiveField;
    private ComboField<String> tagReplaceField;
    private LongTextField textField;

    public TagImagesDialog(String title) {
        super(MainWindow.getInstance(), title, true);
        this.browseMode = MainWindow.getInstance().getBrowseMode();
        setSize(new Dimension(680, 530));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private void applyTags() {
        MultiProgressDialog dialog = new MultiProgressDialog(this, "Tag images");
        BatchTagThread thread;

        // The action that spawns this dialog checks that either current directory or current image set is not null.
        if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM) {
            thread = new BatchTagThread(MainWindow.getInstance().getCurrentDirectory(),
                                          recursiveField.getSelectedIndex() == 1,
                                          getTagOp(),
                                          TagList.of(textField.getText()));
        }
        else {
            thread = new BatchTagThread(MainWindow.getInstance().getImageSetPanel().getSelectedImageSet().get(),
                                        getTagOp(), TagList.of(textField.getText()));
        }

        thread.addProgressListener(new SimpleProgressAdapter() {
            @Override
            public void progressCanceled() {
                log.info("Batch tagging operation was canceled.");
            }

            @Override
            public void progressComplete() {
                dispose();
                MainWindow.getInstance().reloadCurrentImage();
                int countCreated = thread.getCountCreated();
                int countUpdated = thread.getCountUpdated();
                MainWindow.getInstance().showMessageDialog(
                        "Tag batch complete",
                             "Tagging complete: "+thread.getTotalProcessed()+" images processed " +
                        "(" + countCreated + " new tag files created, "+countUpdated+" updated).");
            }
        });
        dialog.runWorker(thread, true);
    }

    private TaggingOperation getTagOp() {
        return TaggingOperation.fromLabel(tagReplaceField.getSelectedItem()).orElse(TaggingOperation.ADD);
    }

    /**
     * Adds the given tag to the text box if it's not already there, or removes it if it is there.
     */
    private void toggleTag(String tag) {
        TagList tagList = TagList.of(textField.getText());
        if (tagList.hasTag(tag)) {
            tagList.remove(tag);
        }
        else {
            tagList.add(tag);
        }
        textField.setText(tagList.toString());
    }

    private JPanel buildFormPanel() {
        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(10);

        recursiveField = new ComboField<>("Batch type:", browseMode == MainWindow.BrowseMode.FILE_SYSTEM
                ? List.of(recurseOptionsFileSystem) : List.of(recurseOptionsImageSet), 0);
        formPanel.add(recursiveField);

        tagReplaceField = new ComboField<>("Action type:", TaggingOperation.getLabels(), 0);
        formPanel.add(tagReplaceField);

        textField = LongTextField.ofDynamicSizingMultiLine("Tags:", 6);
        textField.setMargins(new Margins(16, 20, 0, 0, 4));
        textField.setHelpText("<html>Enter tags as a comma-separated list.<br>Whitespace is ignored between tags.<br>Redundant tags are ignored.</html>");
        textField.setAllowBlank(false);
        formPanel.add(textField);

        PanelField panelField = new PanelField(new BorderLayout());
        FormPanel labelPanel = new FormPanel(Alignment.TOP_LEFT);
        labelPanel.add(LabelField.createBoldHeaderLabel("Substitution tokens", 14).setMargins(new Margins(10,32,10,2,0)));
        labelPanel.add(LabelField.createPlainHeaderLabel("You can use the following tokens to create tags dynamically:", 12).setMargins(new Margins(10,4,10,6,0)));

        String[][] labelStrings;

        if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM) {
            labelStrings = new String[][]{
                    {"<html><b>$(imageDirName)</b></html>:", "The name of the containing directory", "$(imageDirName)"},
                    {"<html><b>$(imageDirPath)</b></html>:", "The full path of the containing directory", "$(imageDirPath)"},
                    {"<html><b>$(parentDirName)</b></html>:", "The name of the containing directory's parent directory", "$(parentDirName)"},
                    {"<html><b>$(parentDirPath)</b></html>:", "The full path of the containing directory's parent directory", "$(parentDirPath)"},
                    {"<html><b>$(aspectRatio)</b></html>:", "A fixed value of \"landscape\", \"portrait\", or \"square\"", "$(aspectRatio)"}
            };
        }
        else {
            labelStrings = new String[][] {
                    {"<html><b>$(imageSetName)</b></html>:", "The name of this image set", "$(imageSetName)"},
                    {"<html><b>$(imageSetPath)</b></html>:", "The fully qualified path of this image set", "$(imageSetPath)"},
                    {"<html><b>$(aspectRatio)</b></html>:", "A fixed value of \"landscape\", \"portrait\", or \"square\"", "$(aspectRatio)"}
            };
        }

        for (String[] labelString : labelStrings) {
            LabelField labelField = (LabelField)new LabelField(labelString[0], labelString[1])
                    .setMargins(new Margins(38, 4, 4, 2, 8));
            LabelField.setLabelHyperlink(labelField.getFieldLabel(), new FieldLabelAction(this, labelString[2]));
            labelPanel.add(labelField);
        }

        panelField.getPanel().add(labelPanel, BorderLayout.CENTER);
        panelField.setShouldExpand(true);
        formPanel.add(panelField);

        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("Apply");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(e -> {
            if (formPanel.isFormValid()) {
                applyTags();
            }
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

    private static class FieldLabelAction extends AbstractAction {

        private final TagImagesDialog owner;
        private final String tag;

        public FieldLabelAction(TagImagesDialog owner, String tag) {
            this.owner = owner;
            this.tag = tag;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            owner.toggleTag(tag);
        }
    }
}
