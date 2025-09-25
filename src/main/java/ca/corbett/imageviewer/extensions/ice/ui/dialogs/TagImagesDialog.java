package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;

/**
 * For batch-tagging a directory of images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagImagesDialog extends JDialog {

    private static final String[] recurseOptions = {
            "Tag all images in this directory",
            "Tag all images in this directory recursively"
    };
    private static final String[] replaceOptions = {
            "Add these tags to the existing tag lists",
            "Remove existing tag lists and replace with these tags"
    };

    private FormPanel formPanel;
    private final File startDir;
    private ComboField<String> recursiveField;
    private ComboField<String> tagReplaceField;
    private LongTextField textField;

    public TagImagesDialog(String title, File startDir) {
        super(MainWindow.getInstance(), title, true);
        setSize(new Dimension(680, 550));
        setResizable(false);
        this.startDir = startDir;
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private void applyTags() {
        MultiProgressDialog dialog = new MultiProgressDialog(this, "Tag images");
        final BatchTagThread thread = new BatchTagThread(startDir,
                                                         recursiveField.getSelectedIndex() == 1,
                                                         tagReplaceField.getSelectedIndex() == 1,
                                                         TagList.of(textField.getText()));
        thread.addProgressListener(new SimpleProgressAdapter() {
            @Override
            public void progressCanceled() {
                // show a message maybe?
            }

            @Override
            public void progressComplete() {
                dispose();
                MainWindow.getInstance().reloadCurrentImage();
            }
        });
        dialog.runWorker(thread, true);
    }

    private JPanel buildFormPanel() {
        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(10);

        recursiveField = new ComboField<>("Batch type:", List.of(recurseOptions), 0);
        formPanel.add(recursiveField);

        tagReplaceField = new ComboField<>("Action type:", List.of(replaceOptions), 0);
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
        labelPanel.add(new LabelField("<html><b>$(imageDirName)</b></html>:", "The name of the containing directory").setMargins(new Margins(38,4,4,2,8)));
        labelPanel.add(new LabelField("<html><b>$(imageDirPath)</b></html>:", "The full path of the containing directory").setMargins(new Margins(38,2,4,2,8)));
        labelPanel.add(new LabelField("<html><b>$(parentDirName)</b></html>:", "The name of the containing directory's parent directory").setMargins(new Margins(38,2,4,2,8)));
        labelPanel.add(new LabelField("<html><b>$(parentDirPath)</b></html>:", "The full path of the containing directory's parent directory").setMargins(new Margins(38,2,4,2,8)));
        labelPanel.add(new LabelField("<html><b>$(aspectRatio)</b></html>:", "A fixed value of \"landscape\", \"portrait\", or \"square\"").setMargins(new Margins(38,2,4,4,8)));
        labelPanel.add(LabelField.createPlainHeaderLabel("Note: using $(aspectRatio) may significantly slow down the operation.", 12).setMargins(new Margins(10,4,10,6,0)));
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

}
