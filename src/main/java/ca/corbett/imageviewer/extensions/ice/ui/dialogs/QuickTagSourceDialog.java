package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ice.ui.QuickTagPanel;
import ca.corbett.imageviewer.ui.MainWindow;
import org.apache.commons.io.FileUtils;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides a way to select, create, duplicate, or delete a
 * quick tag source directory. The only caveat is that you can't
 * delete the default directory - this ensures there will always
 * be at least one.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class QuickTagSourceDialog extends JDialog {

    private static final Logger log = Logger.getLogger(QuickTagSourceDialog.class.getName());

    private final File rootDir;
    private final String originalSourceName;
    private boolean wasOkayed;
    private String selectedSourceName;

    private ComboField<String> sourceCombo;

    public QuickTagSourceDialog(String dialogTitle, String currentSourceName) {
        super(MainWindow.getInstance(), dialogTitle, true);
        this.originalSourceName = currentSourceName;
        this.rootDir = new File(Version.SETTINGS_DIR, "quickTags");
        setSize(new Dimension(400, 250));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    public boolean wasOkayed() {
        return wasOkayed;
    }

    public boolean wasSourceChanged() {
        return !originalSourceName.equals(selectedSourceName);
    }

    public String getSelectedSourceName() {
        return selectedSourceName;
    }

    private void close(boolean okay) {
        wasOkayed = okay;
        if (okay) {
            selectedSourceName = sourceCombo.getSelectedItem();
        }
        dispose();
    }

    private List<String> findSourceDirs() {
        List<File> subdirs = FileSystemUtil.findSubdirectories(new File(Version.SETTINGS_DIR, "quickTags"), false);
        return subdirs.stream().map(File::getName).toList();
    }

    private String promptForName() {
        String name = JOptionPane.showInputDialog(this, "Enter new source name:");
        if (name != null) {
            if (QuickTagPanel.DEFAULT_SOURCE_NAME.equals(name)) {
                JOptionPane.showMessageDialog(this, "You cannot use this name.");
                return null;
            }
            File testFile = new File(rootDir, name);
            if (testFile.exists()) {
                JOptionPane.showMessageDialog(this, "This name is already in use.");
                return null;
            }
        }
        return name;
    }

    private void addSourceOption(String newOption) {
        //noinspection unchecked
        JComboBox<String> combo = (JComboBox<String>)sourceCombo.getFieldComponent();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)combo.getModel();
        model.addElement(newOption);
        sourceCombo.setSelectedIndex(sourceCombo.getItemCount()-1);
    }

    private void createSource() {
        String name = promptForName();
        if (name != null) {
            addSourceOption(name);
        }
    }

    private void duplicateSource() {
        String name = promptForName();
        if (name != null) {
            File sourceDir = getSelectedDir();
            addSourceOption(name);
            File targetDir = getSelectedDir();
            try {
                FileUtils.copyDirectory(sourceDir, targetDir);
            }
            catch (IOException ioe) {
                JOptionPane.showMessageDialog(this, "Error duplicating source: "+ioe.getMessage());
                log.log(Level.SEVERE, "QuickTagSourceDialog: error duplicating source directory: "+ioe.getMessage(), ioe);
            }
        }
    }

    private void deleteSource() {
        String source = sourceCombo.getSelectedItem();
        if (QuickTagPanel.DEFAULT_SOURCE_NAME.equals(source)) {
            JOptionPane.showMessageDialog(this, "You cannot delete the default source.");
            return;
        }
        try {
            FileUtils.deleteDirectory(getSelectedDir());
        }
        catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Error deleting source: "+ioe.getMessage());
            log.log(Level.SEVERE, "QuickTagSourceDialog: error deleting source directory: "+ioe.getMessage(), ioe);
        }

        // Also remove from the combo box:
        //noinspection unchecked
        JComboBox<String> combo = (JComboBox<String>)sourceCombo.getFieldComponent();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)combo.getModel();
        model.removeElementAt(combo.getSelectedIndex());
        sourceCombo.setSelectedIndex(0); // safe default after deletion
    }

    private File getSelectedDir() {
        String source = sourceCombo.getSelectedItem();
        if (QuickTagPanel.DEFAULT_SOURCE_NAME.equals(source)) {
            return rootDir;
        }
        return new File(rootDir, source);
    }

    private JComponent buildFormPanel() {
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);
        formPanel.setBorderMargin(12);

        List<String> sources = new ArrayList<>();
        sources.add(QuickTagPanel.DEFAULT_SOURCE_NAME);
        sources.addAll(findSourceDirs());
        int selectedIndex = 0;
        for (int i = 0; i < sources.size(); i++) {
            if (originalSourceName.equals(sources.get(i))) {
                selectedIndex = i;
                break;
            }
        }
        sourceCombo = new ComboField<>("Source:", sources, selectedIndex);
        formPanel.add(sourceCombo);

        PanelField buttonGroup = new PanelField(new FlowLayout(FlowLayout.LEFT));
        JButton button = new JButton("Create");
        button.setPreferredSize(new Dimension(110, 25));
        buttonGroup.getMargins().setBottom(0);
        buttonGroup.getMargins().setLeft(32);
        buttonGroup.getPanel().add(button);
        button.addActionListener(e -> createSource());
        formPanel.add(buttonGroup);

        buttonGroup = new PanelField(new FlowLayout(FlowLayout.LEFT));
        button = new JButton("Duplicate");
        button.setPreferredSize(new Dimension(110, 25));
        buttonGroup.getMargins().setBottom(0);
        buttonGroup.getMargins().setTop(0);
        buttonGroup.getMargins().setLeft(32);
        buttonGroup.getPanel().add(button);
        button.addActionListener(e -> duplicateSource());
        formPanel.add(buttonGroup);

        buttonGroup = new PanelField(new FlowLayout(FlowLayout.LEFT));
        button = new JButton("Delete");
        button.setPreferredSize(new Dimension(110, 25));
        buttonGroup.getMargins().setTop(0);
        buttonGroup.getMargins().setBottom(0);
        buttonGroup.getMargins().setLeft(32);
        buttonGroup.getPanel().add(button);
        button.addActionListener(e -> deleteSource());
        formPanel.add(buttonGroup);

        return formPanel;
    }

    private JComponent buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(actionEvent -> close(true));
        panel.add(button);

        // Cancel kind of makes no sense when we do changes live...
//        button = new JButton("Cancel");
//        button.setPreferredSize(new Dimension(90,23));
//        button.addActionListener(actionEvent -> close(false));
//        panel.add(button);

        return panel;
    }
}
