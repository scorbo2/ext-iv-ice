package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.ListField;
import ca.corbett.forms.fields.PanelField;
import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.ui.QuickTagPanel;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Provides a dialog for viewing and editing the contents of a quick tag group, with options
 * to add, remove, and reorder the list of tags.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class QuickTagGroupEditDialog extends JDialog {

    private final TagList tagList;
    private final TagList modifiedTagList;
    private final String originalGroupName;
    private String modifiedGroupName;
    private boolean wasOkayed = false;
    private FormPanel formPanel;
    private ShortTextField nameField;
    private ListField<String> listField;

    public QuickTagGroupEditDialog(String dialogTitle, String groupName, TagList tagList) {
        super(MainWindow.getInstance(), dialogTitle, true);
        this.tagList = tagList;
        this.originalGroupName = groupName;
        modifiedGroupName = groupName;
        setSize(new Dimension(660, 430));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        modifiedTagList = new TagList();
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    public boolean wasOkayed() {
        return wasOkayed;
    }

    public TagList getModifiedTagList() {
        return modifiedTagList;
    }

    public boolean groupWasRenamed() {
        return ! originalGroupName.equals(modifiedGroupName);
    }

    public String getModifiedGroupName() {
        return modifiedGroupName;
    }

    private JComponent buildFormPanel() {
        formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(12);

        nameField = new ShortTextField("Group name:", 20);
        nameField.setText(originalGroupName);
        nameField.setAllowBlank(false);
        nameField.addFieldValidator(new NameFieldValidator(tagList.getPersistenceFile(), originalGroupName));
        formPanel.add(nameField);

        formPanel.add(LabelField.createPlainHeaderLabel("Drag+drop or ctrl+up/ctrl+down to reorder, DEL to remove"));
        listField = new ListField<>("", tagList.getTags());
        listField.setVisibleRowCount(10);
        listField.setShouldExpand(true);
        JList<String> list = listField.getList();
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new ListReorderTransferHandler());
        setupKeyboardShortcuts(list);
        formPanel.add(listField);
        formPanel.add(buildListOptionsPanel());

        return formPanel;
    }

    private JComponent buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(actionEvent -> close(true));
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90,23));
        button.addActionListener(actionEvent -> close(false));
        panel.add(button);

        return panel;
    }

    private FormField buildListOptionsPanel() {
        PanelField panelField = new PanelField(new FlowLayout(FlowLayout.LEFT));
        panelField.setShouldExpand(true);
        JPanel wrapper = panelField.getPanel();

        JButton button = new JButton("Add tag");
        button.setPreferredSize(new Dimension(110, 23));
        button.addActionListener(actionEvent -> addTag());
        wrapper.add(button);

        button = new JButton("Remove tag");
        button.setPreferredSize(new Dimension(110, 23));
        button.addActionListener(actionEvent -> removeTag());
        wrapper.add(button);

        button = new JButton("Sort by tag");
        button.setPreferredSize(new Dimension(110, 23));
        button.addActionListener(actionEvent -> sortByName());
        wrapper.add(button);

        button = new JButton("Reverse sort");
        button.setPreferredSize(new Dimension(110, 23));
        button.addActionListener(actionEvent -> reverseSort());
        wrapper.add(button);

        panelField.getMargins().setBottom(32);
        return panelField;
    }

    private void addTag() {
        String tag = JOptionPane.showInputDialog(this, "Add tag");
        if (tag != null) {
            ((DefaultListModel<String>)listField.getListModel()).addElement(tag);
        }
    }

    private void removeTag() {
        int[] selected = listField.getSelectedIndexes();
        if (selected.length == 0) {
            JOptionPane.showMessageDialog(this, "Nothing selected.", "Remove tag", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ((DefaultListModel<String>)listField.getListModel()).remove(selected[0]); // single select list
    }

    private void sortByName() {
        sortList(Comparator.comparing(String::toString));
    }

    private void reverseSort() {
        DefaultListModel<String> listModel = (DefaultListModel<String>)listField.getListModel();
        if (listModel.size() <= 1) {
            return; // don't bother
        }
        TagList copy = new TagList();
        for (int i = listModel.getSize() - 1; i >= 0; i--) {
            copy.add(listModel.getElementAt(i));
        }
        setListContents(copy.getTags());
    }

    private void sortList(Comparator<String> comparator) {
        DefaultListModel<String> listModel = (DefaultListModel<String>)listField.getListModel();
        if (listModel.size() <= 1) {
            return; // don't bother
        }
        TagList copy = new TagList();
        for (int i = 0; i < listModel.size(); i++) {
            copy.add(listModel.getElementAt(i));
        }
        List<String> list = copy.getTags();
        list.sort(comparator);
        setListContents(list);
    }

    private void setListContents(List<String> list) {
        DefaultListModel<String> listModel = (DefaultListModel<String>)listField.getListModel();
        listModel.clear();
        for (String tag : list) {
            listModel.addElement(tag);
        }
    }

    private void close(boolean okay) {
        if (okay) {
            if (! formPanel.isFormValid()) {
                return;
            }
            DefaultListModel<String> listModel = (DefaultListModel<String>)listField.getListModel();
            modifiedTagList.clear();
            for (int i = 0; i < listModel.getSize(); i++) {
                modifiedTagList.add(listModel.getElementAt(i));
            }
            modifiedGroupName = nameField.getText();
        }

        else { // cancel
            modifiedTagList.clear();
            modifiedGroupName = originalGroupName;
        }

        wasOkayed = okay;
        dispose();
    }

    /**
     * Sets up keyboard shortcuts for moving items up and down in the list.
     */
    private void setupKeyboardShortcuts(JList<String> list) {
        // Input maps for key bindings
        InputMap inputMap = list.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = list.getActionMap();

        // Move up shortcut (Ctrl+Up)
        inputMap.put(KeyStroke.getKeyStroke("ctrl UP"), "moveUp");
        actionMap.put("moveUp", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelectedItem(list, -1);
            }
        });

        // Move down shortcut (Ctrl+Down)
        inputMap.put(KeyStroke.getKeyStroke("ctrl DOWN"), "moveDown");
        actionMap.put("moveDown", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelectedItem(list, 1);
            }
        });

        // Remove (delete)
        inputMap.put(KeyStroke.getKeyStroke("DELETE"), "remove");
        actionMap.put("remove", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                removeSelectedItem(list);
            }
        });
    }

    /**
     * Moves the selected item in the list by the specified offset.
     * @param list The JList to modify
     * @param offset Direction to move (-1 for up, 1 for down)
     */
    private void moveSelectedItem(JList<String> list, int offset) {
        DefaultListModel<String> listModel = (DefaultListModel<String>) list.getModel();
        int selectedIndex = list.getSelectedIndex();

        if (selectedIndex == -1) {
            return; // No selection
        }

        int newIndex = selectedIndex + offset;

        // Check bounds
        if (newIndex < 0 || newIndex >= listModel.getSize()) {
            return; // Can't move beyond bounds
        }

        // Move the item
        String item = listModel.getElementAt(selectedIndex);
        listModel.removeElementAt(selectedIndex);
        listModel.insertElementAt(item, newIndex);

        // Maintain selection on moved item
        list.setSelectedIndex(newIndex);
        list.ensureIndexIsVisible(newIndex);
    }

    private void removeSelectedItem(JList<String> list) {
        DefaultListModel<String> listModel = (DefaultListModel<String>)list.getModel();
        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex == -1) {
            return;
        }

        listModel.removeElementAt(selectedIndex);
    }

    /**
     * Custom TransferHandler that handles dragging and dropping list items
     * to reorder them within the same list.
     */
    private static class ListReorderTransferHandler extends TransferHandler {
        private static final DataFlavor LOCAL_OBJECT_FLAVOR =
                new DataFlavor(Integer.class, "application/x-java-Integer");

        private static final DataFlavor[] SUPPORTED_FLAVORS = {LOCAL_OBJECT_FLAVOR};

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JList<?> list = (JList<?>) c;
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex < 0) {
                return null;
            }
            return new IntegerTransferable(selectedIndex);
        }

        @Override
        protected void exportDone(JComponent c, Transferable t, int action) {
            // Clean up is handled in importData
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            return support.isDataFlavorSupported(LOCAL_OBJECT_FLAVOR);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
            int dropIndex = dropLocation.getIndex();

            JList<Object> list = (JList<Object>) support.getComponent();
            DefaultListModel<Object> model = (DefaultListModel<Object>) list.getModel();

            try {
                Integer sourceIndex = (Integer) support.getTransferable()
                                                       .getTransferData(LOCAL_OBJECT_FLAVOR);

                if (sourceIndex == null || sourceIndex == dropIndex) {
                    return false;
                }

                // Remove the item from its original position
                Object item = model.getElementAt(sourceIndex);
                model.removeElementAt(sourceIndex);

                // Adjust drop index if we removed an item before the drop position
                if (sourceIndex < dropIndex) {
                    dropIndex--;
                }

                // Insert the item at its new position
                model.insertElementAt(item, dropIndex);

                // Select the moved item
                list.setSelectedIndex(dropIndex);

                return true;

            } catch (UnsupportedFlavorException |
                     IOException e) {
                return false;
            }
        }
    }

    /**
     * Simple Transferable implementation for transferring integer indices.
     */
    private static class IntegerTransferable implements Transferable {
        private final Integer value;

        public IntegerTransferable(Integer value) {
            this.value = value;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{ListReorderTransferHandler.LOCAL_OBJECT_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return ListReorderTransferHandler.LOCAL_OBJECT_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return value;
        }
    }

    private static class NameFieldValidator implements FieldValidator<ShortTextField> {

        private final File targetDirectory;
        private final String originalName;

        public NameFieldValidator(File sourceFile, String originalName) {
            this.targetDirectory = sourceFile.getParentFile();
            this.originalName = originalName;
        }

        @Override
        public ValidationResult validate(ShortTextField fieldToValidate) {
            boolean isModified = ! originalName.equals(fieldToValidate.getText());
            if (! isModified) {
                return ValidationResult.valid();
            }

            // Reject reserved name:
            if (QuickTagPanel.DEFAULT_SOURCE_NAME.equals(fieldToValidate.getText())) {
                return ValidationResult.invalid("You cannot use this name.");
            }

            String filename = fieldToValidate.getText() + ".json";
            File candidateFile = new File(targetDirectory, filename);
            return candidateFile.exists()
                    ? ValidationResult.invalid("A group with that name already exists.")
                    : ValidationResult.valid();
        }
    }
}
