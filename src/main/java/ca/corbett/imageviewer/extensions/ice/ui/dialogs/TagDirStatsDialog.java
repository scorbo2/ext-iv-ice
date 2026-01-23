package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.builtin.DirectoryInfoDialog;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ThumbContainerPanel;
import org.apache.commons.io.FilenameUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Shows statistics about tagged/untagged images in a directory, with optional recursion.
 * This dialog is only available in file system browse mode.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2.4.0
 */
public class TagDirStatsDialog extends JDialog {

    private final static Logger log = Logger.getLogger(TagDirStatsDialog.class.getName());

    private final File directory;
    private final CheckBoxField recursiveField;
    private final LabelField totalLabel;
    private final LabelField taggedLabel;
    private final LabelField untaggedLabel;
    private final LabelField averageLabel;
    private boolean isScanInProgress = false;

    public TagDirStatsDialog(Frame owner, File dir) {
        super(owner, "Directory tag stats", true);
        this.directory = dir;

        recursiveField = new CheckBoxField("Include subdirectories", true);
        recursiveField.addValueChangedListener(e -> rescan());
        totalLabel = new LabelField("Total images: ", "0");
        taggedLabel = new LabelField("Total tagged: ", "0");
        untaggedLabel = new LabelField("Total untagged: ", "0");
        averageLabel = new LabelField("Average tags: ", "0");
        FormPanel formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(16);
        formPanel.add(List.of(
                recursiveField,
                totalLabel,
                taggedLabel,
                untaggedLabel,
                averageLabel
        ));

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        setSize(400, 300);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        rescan();
    }

    private void rescan() {
        if (directory == null || ! directory.isDirectory()) {
            log.warning("No directory selected.");
            return;
        }

        // If a scan is already in progress, ignore this request
        if (isScanInProgress) {
            log.fine("Scan already in progress, ignoring rescan request.");
            return;
        }

        // Set the flag to prevent concurrent scans
        isScanInProgress = true;

        // Fire off a worker thread as the scan may take some time:
        boolean isRecursive = recursiveField.isChecked();
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Scanning directory...");
        progressDialog.setInitialShowDelayMS(500);
        progressDialog.runWorker(new TagDirStatsDialog.ScanWorker(directory, isRecursive), true);
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton button = new JButton("Rescan");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> rescan());
        buttonPanel.add(button);

        button = new JButton("OK");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> dispose());
        buttonPanel.add(button);

        return buttonPanel;
    }

    /**
     * Returns the companion ice file for the given image file, if there is one.
     */
    public static File getICEFile(File imageFile) {
        // Check if a matching .ice file exists in same dir:
        File testFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName())+".ice");
        if (testFile.exists()) {
            return testFile;
        }

        return null;
    }


    /**
     * An internal worker class to scan the directory in a background thread.
     */
    private class ScanWorker extends SimpleProgressWorker {

        private final File dir;
        private final boolean recursive;
        private int imageCount;
        private int taggedCount;
        private int untaggedCount;
        private int averageTags;

        public ScanWorker(File dir, boolean recursive) {
            this.dir = dir;
            this.recursive = recursive;
        }

        @Override
        public void run() {
            imageCount = 0;
            taggedCount = 0;
            untaggedCount = 0;
            averageTags = 0;

            int totalTagsFound = 0;

            ImageViewerExtensionManager extManager = ImageViewerExtensionManager.getInstance();
            List<File> allFiles = FileSystemUtil.findFiles(dir, recursive, ThumbContainerPanel.getImageExtensions());
            fireProgressBegins(allFiles.size());

            try {
                for (int i = 0; i < allFiles.size(); i++) {
                    File candidate = allFiles.get(i);
                    imageCount++;

                    File iceFile = getICEFile(candidate);
                    if (iceFile != null) {
                        taggedCount++;
                        totalTagsFound += TagList.fromFile(iceFile).size();
                    } else {
                        untaggedCount++;
                    }

                    // Update progress
                    fireProgressUpdate(i, "Processing...");
                }

                averageTags = (taggedCount > 0) ? (totalTagsFound / taggedCount) : 0;

                // Update UI fields with final counts, but do it on the Swing EDT thread:
                SwingUtilities.invokeLater(() -> {
                    totalLabel.setText(imageCount + " images");
                    taggedLabel.setText(taggedCount + " tagged");
                    untaggedLabel.setText(untaggedCount + " untagged");
                    averageLabel.setText(averageTags + " tags/image");
                });
            }
            finally {
                // Clear the flag to allow future scans:
                SwingUtilities.invokeLater(() -> {
                    isScanInProgress = false;
                });

                // Don't forget to fire complete event!
                // Otherwise, the progress dialog hangs around.
                fireProgressComplete();
            }
        }
    }
}
