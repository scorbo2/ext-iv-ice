package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.PropertiesManager;
import ca.corbett.extras.properties.ShortTextProperty;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.llm.AiConnectionManager;
import ca.corbett.imageviewer.extensions.ice.llm.AiErrorBody;
import ca.corbett.imageviewer.extensions.ice.llm.AiRequestThread;
import ca.corbett.imageviewer.ui.MainWindow;
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
import java.util.List;
import java.util.logging.Logger;

/**
 * Shows a dialog that allows auto-tagging of all jpeg and/or png images in the selected
 * directory, with optional recursion. This feature is experimental and subject to change!
 * <p>
 * Note that batch auto-tagging, unlike single-image auto-tagging, will automatically update
 * the affected image(s) with the suggested tags, with no opportunity to review or edit them.
 * This is a bit of a YOLO option, especially if you have not constrained the LLM with
 * a restricted tag list. If any auto-tag request fails, the batch is aborted. Tags that
 * have already been applied at that point in the operation have already been saved.
 * (There is no "transaction rollback" option here... maybe a future feature).
 * Same thing applies if the batch operation is canceled - all tags that have already
 * been received from the LLM at that point are already saved. "Cancel" just stops
 * the operation from proceeding any further.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.3.0
 */
public class AutoTagBatchDialog extends JDialog {

    private static final Logger log = Logger.getLogger(AutoTagBatchDialog.class.getName());
    private static final String NAME = "Auto-tag batch";

    private final AiConnectionManager aiManager;
    private final File dir;
    private final List<File> eligibleImages = new ArrayList<>();
    private final CheckBoxField recursiveField;
    private final LabelField imageCountLabel;
    private final CheckBoxField useConfigRestrictionField;
    private final ShortTextField tagRestrictionField;
    private boolean isOperationInProgress;

    public AutoTagBatchDialog(Frame owner, File dir, AiConnectionManager aiManager) {
        super(owner, NAME, true);
        this.dir = dir;
        this.aiManager = aiManager;
        recursiveField = new CheckBoxField("Include subdirectories", true);
        recursiveField.addValueChangedListener(e -> rescan());
        imageCountLabel = new LabelField("Image count:", "0");
        imageCountLabel.setHelpText("Note: Only JPEG and PNG images are counted here.");
        tagRestrictionField = new ShortTextField("Tag restriction", 20);
        tagRestrictionField.setHelpText("<html>Restrict the LLM to only these tags." +
                                                "<br>If blank, the LLM is free to decide which tags to use.</html>");
        tagRestrictionField.setEnabled(false);
        tagRestrictionField.setText(getRestrictionList());
        useConfigRestrictionField = new CheckBoxField("Use tag restrictions from configuration", true);
        useConfigRestrictionField.addValueChangedListener(
                e -> tagRestrictionField.setEnabled(!useConfigRestrictionField.isChecked()));
        FormPanel formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(16);
        formPanel.add(List.of(
                recursiveField,
                imageCountLabel,
                useConfigRestrictionField,
                tagRestrictionField
        ));

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        setSize(600, 240);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Force an immediate rescan to populate the image count:
        rescan();
    }

    /**
     * Executes a search of the current directory to find all eligible images.
     * We want to do this before the user starts the operation, so they have some
     * idea of how many requests they're about to make. The token usage might
     * be considerable, so we should give them a heads-up in advance.
     */
    private void rescan() {
        if (dir == null || !dir.isDirectory()) {
            log.warning("No directory selected.");
            return;
        }

        // If a scan or a batch operation is already in progress, ignore this request
        if (isOperationInProgress) {
            log.fine("Operation already in progress, ignoring rescan request.");
            return;
        }

        // Set the flag to prevent concurrent scans:
        isOperationInProgress = true;

        // Fire off a worker thread as the scan may take some time:
        boolean isRecursive = recursiveField.isChecked();
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Scanning directory...");
        progressDialog.runWorker(new ScanWorker(dir, isRecursive), true);
    }

    /**
     * Starts a worker thread to do the batch auto-tagging.
     */
    private void doBatch() {
        // Quick sanity check: if we have no images, there's nothing we can do here:
        if (eligibleImages.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "No eligible images found in the selected directory.");
            log.info("No eligible images found, aborting batch auto-tag.");
            return;
        }

        // If a scan or a batch operation is already in progress, ignore this request:
        if (isOperationInProgress) {
            log.fine("Operation already in progress, ignoring request.");
            return;
        }

        // Override the tag restriction list if the user has given us a custom value here:
        TagList restrictionTags = TagList.of(getRestrictionList()); // might be empty
        if (!useConfigRestrictionField.isChecked()) {
            restrictionTags = TagList.of(tagRestrictionField.getText()); // might be empty
        }
        aiManager.setLlmTags(restrictionTags); // okay if empty - LLM will choose tags

        // Emit a log warning if there are no tag restrictions:
        if (aiManager.getLlmTags().isEmpty()) {
            // This may result in very unexpected behavior. Perhaps the user is unaware of the consequences here:
            log.warning("LLM tags list is empty - the LLM will be free to choose any tags it wants!" +
                                " This may result in unpredictable or inconsistent tags being chosen. " +
                                "You can supply a tag list in configuration to restrict the LLM.");
        }

        // Set the flag to prevent concurrent batch operations:
        isOperationInProgress = true;

        // Fire off a BatchWorker:
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Auto-tagging images...");
        progressDialog.runWorker(new BatchWorker(eligibleImages), true);
    }

    /**
     * Returns the configured restricted tag list from application settings.
     * Will return a blank string if the user has not configured any.
     */
    private String getRestrictionList() {
        PropertiesManager propsManager = AppConfig.getInstance().getPropertiesManager();
        AbstractProperty prop = propsManager.getProperty(IceExtension.llmTagsProp);
        String restrictedTags = "";
        if (prop instanceof ShortTextProperty tagsProp) {
            restrictedTags = tagsProp.getValue();
        }
        return restrictedTags;
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton button = new JButton("Rescan");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> rescan());
        buttonPanel.add(button);

        button = new JButton("Auto-tag!");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> doBatch());
        buttonPanel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(100, 25));
        button.addActionListener(e -> dispose());
        buttonPanel.add(button);

        return buttonPanel;
    }

    /**
     * A very simple worker to wrap the FileSystemUtil findFiles call, which may take some time
     * if we're in a very large directory. The thread updates our image count label, and also
     * populates the eligibleImages list, which we will use later when we do the batch auto-tagging.
     * Only one scan can be in progress at a time, as enforced by the isOperationInProgress flag.
     */
    private class ScanWorker extends SimpleProgressWorker {

        private final File dir;
        private final boolean isRecursive;

        public ScanWorker(File dir, boolean isRecursive) {
            this.dir = dir;
            this.isRecursive = isRecursive;
        }

        @Override
        public void run() {
            fireProgressBegins(2);
            try {
                fireProgressUpdate(1, "Scanning for images...");
                List<File> images = FileSystemUtil.findFiles(dir, isRecursive, List.of("jpg", "jpeg", "png"));

                SwingUtilities.invokeLater(() -> imageCountLabel.setText(String.valueOf(images.size())));
                eligibleImages.clear();
                eligibleImages.addAll(images);
            }
            finally {
                SwingUtilities.invokeLater(() -> {
                    isOperationInProgress = false;
                });

                fireProgressComplete(); // make sure the progress dialog closes
            }
        }
    }

    /**
     * A worker thread to spawn an AiRequestThread for each eligible image, one by one, and track
     * their progress. In theory, we could send multiple requests at a time to the LLM server,
     * but we might hit rate-limiting errors. Also, by doing it one-by-one, we have the option
     * of aborting the batch if any of the requests fails. This is a debatable call, but as
     * this is an experimental feature, I think it's fine for now.
     */
    private class BatchWorker extends SimpleProgressWorker {

        private final List<File> imagesToProcess;

        public BatchWorker(List<File> imagesToProcess) {
            // Make a copy of the list to avoid concurrency issues:
            this.imagesToProcess = new ArrayList<>(imagesToProcess);
        }

        private void handleResult(File imageFile, TagList tagList) {
            // An "empty" return is one where we got back either a completely empty list,
            // or a list with just the NO_TAG sentinel value.
            boolean isEmpty = tagList.isEmpty()
                    || (tagList.size() == 1 && tagList.getTags().get(0).equals(AiConnectionManager.NO_TAGS));

            // If we get back such a list, just log it and move on:
            if (isEmpty) {
                log.info("Auto-tag: "
                                 + imageFile.getAbsolutePath()
                                 + ": The LLM had no tag suggestions for this image.");
                return;
            }

            // Find the tag file for this image.
            // It's not an error if this file does not exist... we'll create it.
            log.info("Auto-tag: " + imageFile.getAbsolutePath() + ": adding tags: " + tagList.toString());
            File tagFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName()) + ".ice");
            TagList originalTags = TagList.fromFile(tagFile); // might be empty; that's okay
            originalTags.addAll(tagList); // duplicates are pruned automatically, it's not a problem.
            originalTags.save(); // commit changes to disk
            TagIndex.getInstance().addOrUpdateEntry(imageFile, tagFile); // tell the TagIndex of this change
        }

        private void handleError(File imageFile, AiErrorBody error) {
            log.severe("Auto-tag operation failed: " + error.getMessage()
                               + " (code: " + error.getCode() + ", type: " + error.getType() + ")");

            // If any request fails, we will abort the entire batch and show an error message to the user.
            SwingUtilities.invokeLater(() -> {
                MainWindow.getInstance().showMessageDialog(NAME,
                                                           "Error auto-tagging image: " + imageFile.getName() +
                                                                   "\nError code: " + error.getCode() +
                                                                   "\nMessage: " + error.getMessage() +
                                                                   "\nError type: " + error.getType() +
                                                                   "\n\nAborting batch operation.");
            });

            // We can break out of the loop here, which will cause the batch operation to end.
            // The isOperationInProgress flag will be reset in the finally block, allowing the user to try again if they want.
            throw new RuntimeException("Batch auto-tagging aborted due to error.");
        }

        @Override
        public void run() {
            fireProgressBegins(imagesToProcess.size());
            int step = 0;
            try {
                for (File imageFile : imagesToProcess) {
                    if (!fireProgressUpdate(step++, imageFile.getAbsolutePath())) {
                        log.info("Batch auto-tagging cancelled by user.");
                        break;
                    }

                    AiRequestThread thread = new AiRequestThread(imageFile,
                                                                 aiManager,
                                                                 tagList -> handleResult(imageFile, tagList),
                                                                 errorBody -> handleError(imageFile, errorBody));
                    thread.setChatty(false); // quiet, you!
                    thread.run(); // run the request synchronously in this worker thread, so we can track progress and handle errors appropriately

                    // We'll add a slight, 1s delay between requests, to avoid hammering the server.
                    // Would this help avoid rate-limiting errors? Dunno, maybe. Just seems polite though.
                    Thread.sleep(1000);
                }
            }
            catch (Exception e) {
                log.severe("Batch auto-tagging failed: " + e.getMessage());
                // The error has already been handled in the handleError method, so we don't need to do anything else here.
            }
            finally {
                SwingUtilities.invokeLater(() -> {
                    isOperationInProgress = false;
                    MainWindow.getInstance().reload(); // Reload current dir to show new tags
                    dispose(); // debatable, but probably makes sense to close after a successful batch operation.
                });

                fireProgressComplete(); // make sure the progress dialog closes
            }
        }
    }
}
