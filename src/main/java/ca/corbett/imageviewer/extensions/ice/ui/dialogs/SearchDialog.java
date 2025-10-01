package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.progress.MultiProgressAdapter;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.FileField;
import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.threads.SearchThread;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.imagesets.ImageSet;
import ca.corbett.imageviewer.ui.imagesets.ImageSetManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class SearchDialog extends JDialog {

    private static final Logger log = Logger.getLogger(SearchDialog.class.getName());

    private MessageUtil messageUtil;
    private final File initialDir;
    private FormPanel formPanel;
    private ShortTextField searchNameField;
    private FileField dirField;
    private CheckBoxField recursiveField;
    private ShortTextField tagFieldAll;
    private ShortTextField tagFieldAny;
    private ShortTextField tagFieldNone;

    public SearchDialog() {
        this("Search", null);
    }

    public SearchDialog(File initialDir) {
        this("Search", initialDir);
    }

    public SearchDialog(String title) {
        this(title, null);
    }

    public SearchDialog(String title, File initialDir) {
        super(MainWindow.getInstance(), title, true);
        this.initialDir = initialDir;
        setSize(new Dimension(630, 400));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private void doSearch() {
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Search in progress");
        final SearchThread searchThread = new SearchThread(dirField.getFile(),
                                                           recursiveField.isChecked(),
                                                           TagList.of(tagFieldAll.getText()),
                                                           TagList.of(tagFieldAny.getText()),
                                                           TagList.of(tagFieldNone.getText()));
        searchThread.addProgressListener(new MultiProgressAdapter() {
            @Override
            public void progressCanceled() {
                getMessageUtil().info("Search was canceled.");
            }

            @Override
            public void progressComplete() {
                handleSearchComplete(searchThread.wasCanceled(), searchThread.getSearchResults());
            }
        });
        progressDialog.runWorker(searchThread, true);
    }

    private void handleSearchComplete(boolean wasCanceled, List<File> searchResults) {
        if (wasCanceled) {
            getMessageUtil().info("Search was canceled.");
            return;
        }
        if (searchResults.isEmpty()) {
            getMessageUtil().info("Search returned no results.");
            return;
        }

        String fullyQualifiedName = createImageSetName(searchNameField.getText());
        ImageSet resultSet = new ImageSet(createImageSetName(fullyQualifiedName));
        if (fullyQualifiedName.startsWith("/ICE/")) {
            resultSet.setTransient(true); // don't persist stuff in /ICE/
        }
        for (File result : searchResults) {
            resultSet.addImageFilePath(result.getAbsolutePath());
        }
        MainWindow.getInstance().getImageSetManager().addImageSet(resultSet);
        dispose();
        MainWindow.getInstance().setBrowseMode(MainWindow.BrowseMode.IMAGE_SET, false);
        MainWindow.getInstance().getImageSetPanel().resync(resultSet);
    }

    private FormPanel buildFormPanel() {
        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(16);
        
        searchNameField = new ShortTextField("Search name:", 10);
        searchNameField.setText(getUniqueSearchName());
        searchNameField.setAllowBlank(false);
        searchNameField.addFieldValidator(new FieldValidator<ShortTextField>() {
            @Override
            public ValidationResult validate(ShortTextField fieldToValidate) {
                if (isSearchNameTaken(fieldToValidate.getText())) {
                    return ValidationResult.invalid("Search name is in use!");
                }
                return ValidationResult.valid();
            }
        });
        formPanel.add(searchNameField);

        dirField = new FileField("Directory:", initialDir, 20, FileField.SelectionType.ExistingDirectory);
        formPanel.add(dirField);

        recursiveField = new CheckBoxField("Recursive", true);
        formPanel.add(recursiveField);

        LabelField labelField = LabelField.createBoldHeaderLabel("This search should return images that have...", 12);
        labelField.getMargins().setTop(24);
        formPanel.add(labelField);
        tagFieldAll = new ShortTextField("ALL of these tags:", 28);
        tagFieldAll.setHelpText("Comma-separated.");
        tagFieldAll.getMargins().setLeft(18);
        tagFieldAll.addFieldValidator(new TagFieldValidator());
        formPanel.add(tagFieldAll);
        tagFieldAny = new ShortTextField("ANY of these tags:", 28);
        tagFieldAny.setHelpText("Comma-separated.");
        tagFieldAny.getMargins().setLeft(18);
        tagFieldAny.addFieldValidator(new TagFieldValidator());
        formPanel.add(tagFieldAny);
        tagFieldNone = new ShortTextField("NONE of these tags:", 28);
        tagFieldNone.setHelpText("Comma-separated.");
        tagFieldNone.getMargins().setLeft(18);
        tagFieldNone.addFieldValidator(new TagFieldValidator());
        formPanel.add(tagFieldNone);
        formPanel.add(LabelField.createPlainHeaderLabel("(fill in at least one)"));

        return formPanel;
    }

    /**
     * You can enter a fully qualified name, in which case it's used as-is, or you can
     * just enter a name, in which case we'll stick it under /ICE/.
     */
    private String createImageSetName(String searchName) {
        return searchName.contains(String.valueOf(ImageSetManager.PATH_DELIMITER)) ?
                ImageSetManager.parseFullyQualifiedName(searchName) :
                ImageSetManager.parseFullyQualifiedName("/ICE/"+searchName);
    }

    private boolean isSearchNameTaken(String name) {
        ImageSetManager manager = MainWindow.getInstance().getImageSetManager();
        return manager.findImageSet(createImageSetName(name)).isPresent();
    }

    private String getUniqueSearchName() {
        int attempt = 1;

        while (attempt < 100) {
            String candidateName = "New Search "+attempt;
            if (! isSearchNameTaken(candidateName)) {
                return candidateName;
            }
            attempt++;
        }

        // Fuck it, use a guid:
        return UUID.randomUUID().toString();
    }

    private JPanel buildButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("Search");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(e -> {
            if (formPanel.isFormValid()) {
                doSearch();
            }
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

    private boolean isAtLeastOneTagFieldFilled() {
        TagList searchAll = TagList.of(tagFieldAll.getText());
        TagList searchAny = TagList.of(tagFieldAny.getText());
        TagList searchNone = TagList.of(tagFieldNone.getText());
        return !searchAll.isEmpty() || !searchAny.isEmpty() || !searchNone.isEmpty();
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }

    private class TagFieldValidator implements FieldValidator<ShortTextField> {

        @Override
        public ValidationResult validate(ShortTextField fieldToValidate) {
            return isAtLeastOneTagFieldFilled()
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("At least one tag field must be filled.");
        }
    }
}
