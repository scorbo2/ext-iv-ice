package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ThumbContainerPanel;
import ca.corbett.imageviewer.ui.imagesets.ImageSet;
import ca.corbett.imageviewer.ui.imagesets.ImageSetManager;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Presents a dialog for generating an ImageSet of a configurable size populated
 * with randomly selected images from the current directory, with optional recursion.
 * The resulting ImageSet will be marked as transient by default. The intention
 * is to make locating and tagging untagged and poorly-tagged images easier,
 * especially when faced with a large directory of images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2.4.0
 */
public class RandomImageSetDialog extends JDialog {
    private final static Logger log = Logger.getLogger(RandomImageSetDialog.class.getName());

    private enum TagCriteria {
        Untagged("Untagged images"),
        AtLeastN("Images with at least N tags"),
        AtMostN("Images with at most N tags"),
        ExactlyN("Images with exactly N tags");

        private final String label;

        TagCriteria(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private MessageUtil messageUtil;
    private final File directory;
    private final List<File> imageFiles = new ArrayList<>();
    private final List<TagList> imageTagLists = new ArrayList<>();

    private final CheckBoxField recursiveField;
    private final LabelField totalLabel;
    private final LabelField taggedLabel;
    private final LabelField untaggedLabel;
    private final NumberField howManyField;
    private final ComboField<TagCriteria> tagCriteriaField;
    private final NumberField tagCountField;
    private boolean isScanInProgress = false;

    public RandomImageSetDialog(Frame owner, File dir) {
        super(owner, "Create random image set", true);
        this.directory = dir;

        recursiveField = new CheckBoxField("Include subdirectories", true);
        recursiveField.addValueChangedListener(e -> rescan());
        totalLabel = new LabelField("Total images: ", "0");
        taggedLabel = new LabelField("Total tagged: ", "0");
        untaggedLabel = new LabelField("Total untagged: ", "0");
        howManyField = new NumberField("Max set size:", 50, 10, 500, 10);
        tagCriteriaField = new ComboField<>("Tag criteria:", List.of(TagCriteria.values()), 0);
        tagCountField = new NumberField("Tag count (N):", 1, 0, Integer.MAX_VALUE, 1);
        tagCountField.setVisible(false); // will show when needed

        FormPanel formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(16);
        formPanel.add(List.of(
                recursiveField,
                totalLabel,
                taggedLabel,
                untaggedLabel,
                howManyField,
                tagCriteriaField,
                tagCountField
        ));

        // Make sure our N field is shown or hidden as needed:
        tagCriteriaField.addValueChangedListener(field -> tagCountField.setVisible(tagCriteriaField.getSelectedIndex() != 0));

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        setSize(400, 330);
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
        progressDialog.runWorker(new RandomImageSetDialog.ScanWorker(directory, isRecursive), true);
    }

    private boolean generateImageSet() {
        // Sanity check:
        if (imageFiles.isEmpty() || imageTagLists.isEmpty()) {
            getMessageUtil().info("No images available", "No images were found in the selected directory.");
            return false;
        }
        if (imageFiles.size() != imageTagLists.size()) {
            // *should* never happen, but...
            log.severe("Internal error: image file and tag list counts do not match.");
            getMessageUtil().error("Internal error", "An internal error occurred. Please check the log for details.");
            return false;
        }

        int subsetCount = howManyField.getCurrentValue().intValue();
        int nValue = tagCountField.getCurrentValue().intValue();
        List<File> candidates = switch (tagCriteriaField.getSelectedItem()) {
            case Untagged -> getImagesWithExactlyNTags(0);
            case AtLeastN -> getImagesWithAtLeastNTags(nValue);
            case AtMostN -> getImagesWithAtMostNTags(nValue);
            case ExactlyN -> getImagesWithExactlyNTags(nValue);
        };

        // It may be that we ended up with nothing:
        if (candidates.isEmpty()) {
            getMessageUtil().info("No matching images", "No images matching the selected criteria were found.");
            return false;
        }

        // It may also be that we ended up with fewer images than the user asked for:
        if (candidates.size() < subsetCount) {
            subsetCount = candidates.size();
        }

        // Pull out images randomly until we fill our subset count:
        // Use Fisher-Yates shuffle to do this in O(subsetCount) time:
        Random rand = new Random();
        for (int i = 0; i < subsetCount; i++) {
            int randomIndex = i + rand.nextInt(candidates.size() - i);
            Collections.swap(candidates, i, randomIndex);
        }
        List<File> results = candidates.subList(0, subsetCount);

        ImageSet imageSet = new ImageSet(getUniqueSetName());
        imageSet.setTransient(true); // Mark as transient by default - user can change if they want to save it.
        for (File file : results) {
            imageSet.addImageFilePath(file.getAbsolutePath()); // don't remember why it wants paths instead of Files
        }
        MainWindow.getInstance().getImageSetManager().addImageSet(imageSet);

        // Switch to ImageSet browse mode, and select this new set:
        MainWindow.getInstance().setBrowseMode(MainWindow.BrowseMode.IMAGE_SET, false);
        MainWindow.getInstance().getImageSetPanel().resync(imageSet);
        return true; // dispose this dialog
    }

    /**
     * Returns only images that have exactly N tags.
     */
    private List<File> getImagesWithExactlyNTags(int N) {
        List<File> results = new ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageTagLists.get(i).size() == N) {
                results.add(imageFiles.get(i));
            }
        }
        return results;
    }

    /**
     * Returns only images that have at least N tags.
     */
    private List<File> getImagesWithAtLeastNTags(int N) {
        List<File> results = new ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageTagLists.get(i).size() >= N) {
                results.add(imageFiles.get(i));
            }
        }
        return results;
    }

    /**
     * Returns only images that have at most N tags.
     */
    private List<File> getImagesWithAtMostNTags(int N) {
        List<File> results = new ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageTagLists.get(i).size() <= N) {
                results.add(imageFiles.get(i));
            }
        }
        return results;
    }

    private void buttonHandler(boolean isOkay) {
        if (isOkay) {
            if (! generateImageSet()) {
                return; // generation failed, stay open
            }
        }

        dispose();
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
        button.addActionListener(e -> buttonHandler(true));
        buttonPanel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> buttonHandler(false));
        buttonPanel.add(button);

        return buttonPanel;
    }

    /**
     * Returns the companion ice file for the given image file, if there is one.
     */
    private static File getICEFile(File imageFile) {
        // Check if a matching .ice file exists in same dir:
        File testFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName())+".ice");
        if (testFile.exists()) {
            return testFile;
        }

        return null;
    }

    /**
     * Attempts to generate a unique name for a new ImageSet.
     * If this has already been done 100 times, the new name
     * will be a random GUID. Otherwise, it'll look like:
     * "Random set N".
     * <p>
     * In either case, the returned name will be fully-qualified
     * under the /ICE/ namespace. The user can move it as
     * they see fit.
     * </p>
     */
    private static String getUniqueSetName() {
        ImageSetManager manager = MainWindow.getInstance().getImageSetManager();
        int attempt = 1;

        while (attempt < 100) {
            String candidateName = "/ICE/Random set "+attempt;
            if (! manager.findImageSet(candidateName).isPresent()) {
                return candidateName;
            }
            attempt++;
        }

        // Fuck it, use a guid:
        return "/ICE/" + UUID.randomUUID().toString();
    }


    /**
     * An internal worker class to scan the directory in a background thread.
     * We're looking to collect the following information:
     * <ul>
     *     <li>Total number of images</li>
     *     <li>For each image, get its TagList, if it has one</li>
     * </ul>
     */
    private class ScanWorker extends SimpleProgressWorker {

        private final File dir;
        private final boolean recursive;

        public ScanWorker(File dir, boolean recursive) {
            this.dir = dir;
            this.recursive = recursive;
        }

        @Override
        public void run() {
            imageFiles.clear();
            imageTagLists.clear();
            int taggedCount = 0;
            int untaggedCount = 0;

            List<File> allFiles = FileSystemUtil.findFiles(dir, recursive, ThumbContainerPanel.getImageExtensions());
            fireProgressBegins(allFiles.size());

            try {
                for (int i = 0; i < allFiles.size(); i++) {
                    File candidate = allFiles.get(i);
                    imageFiles.add(candidate);

                    File iceFile = getICEFile(candidate);
                    if (iceFile != null) {
                        taggedCount++;
                        imageTagLists.add(TagList.fromFile(iceFile));
                    } else {
                        untaggedCount++;
                        imageTagLists.add(new TagList());
                    }

                    // Update progress
                    if (! fireProgressUpdate(i+1, "Processing...")) {
                        // User cancelled
                        imageFiles.clear();
                        imageTagLists.clear();
                        break;
                    }
                }

                // Update UI fields with final counts, but do it on the Swing EDT thread:
                final int tagged = taggedCount;
                final int untagged = untaggedCount;
                SwingUtilities.invokeLater(() -> {
                    totalLabel.setText(imageFiles.size() + " images");
                    taggedLabel.setText(tagged + " tagged");
                    untaggedLabel.setText(untagged + " untagged");
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

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(MainWindow.getInstance(), log);
        }
        return messageUtil;
    }
}
