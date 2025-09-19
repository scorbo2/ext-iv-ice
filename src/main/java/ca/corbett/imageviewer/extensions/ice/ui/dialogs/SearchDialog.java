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

    private static final String[] searchOptions = {
            "Find images that have ALL of these tags",
            "Find images that have ANY of these tags",
            "Find images that have NONE of these tags"
    };

    private MessageUtil messageUtil;
    private final File initialDir;
    private FormPanel formPanel;
    private ShortTextField searchNameField;
    private FileField dirField;
    private ComboField<String> searchTypeField;
    private LongTextField tagField;
    private CheckBoxField recursiveField;

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
        setSize(new Dimension(600, 440));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private void doSearch() {
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Search in progress");
        SearchThread.SearchMode searchMode = switch (searchTypeField.getSelectedIndex()) {
            case 0 -> SearchThread.SearchMode.CONTAINS_ALL;
            case 1 -> SearchThread.SearchMode.CONTAINS_ANY;
            default -> SearchThread.SearchMode.CONTAINS_NONE;
        };
        final SearchThread searchThread = new SearchThread(dirField.getFile(), TagList.of(tagField.getText()), recursiveField.isChecked(), searchMode);
        searchThread.addProgressListener(new MultiProgressAdapter() {
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
        MainWindow.getInstance().getImageSetPanel().resync();
        MainWindow.getInstance().getImageSetPanel().selectAndScrollTo(resultSet);
    }

    private FormPanel buildFormPanel() {
        formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(16);
        formPanel.setEnabled(false);

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

        dirField = new FileField("Directory:", initialDir, 22, FileField.SelectionType.ExistingDirectory);
        formPanel.add(dirField);

        recursiveField = new CheckBoxField("Recursive", true);
        formPanel.add(recursiveField);

        tagField = LongTextField.ofFixedSizeMultiLine("Tags:", 3, 31);
        tagField.setHelpText("Comma-separated.");
        tagField.setAllowBlank(false);
        formPanel.add(tagField);

        searchTypeField = new ComboField<>("Search:", List.of(searchOptions), 0);
        formPanel.add(searchTypeField);

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

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }
}
