package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.TagListEditDialog;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;
import ca.corbett.imageviewer.ui.imagesets.ImageSetPanel;
import org.apache.commons.io.FilenameUtils;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuickTagPanel extends JPanel {

    private static final Logger log = Logger.getLogger(QuickTagPanel.class.getName());

    private static final int SideMargin = 12;
    private static final int RowHeight = 25;

    private final File tagDir;
    private int panelWidth;
    private BufferedImage iconAddTag;
    private BufferedImage iconEditTagGroup;
    private BufferedImage iconRemoveTagGroup;

    public QuickTagPanel() {
        setLayout(new GridBagLayout());
        tagDir = new File(Version.SETTINGS_DIR, "quickTags");
        if (!tagDir.exists()) {
            tagDir.mkdirs();
        }
        try {
            iconAddTag = ImageSetPanel.loadIconImage("icon-plus.png");
            iconEditTagGroup = ImageSetPanel.loadIconImage("icon-document-edit.png");
            iconRemoveTagGroup = ImageSetPanel.loadIconImage("icon-x.png");
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "QuickTagPanel: error loading icon images: "+ioe.getMessage(), ioe);
        }
        reset();
    }

    public void reset() {
        IntegerProperty prop = (IntegerProperty)AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelWidthProp);
        panelWidth = prop == null ? 200 : prop.getValue();
        removeAll();
        List<File> tagFiles = FileSystemUtil.findFiles(tagDir, false, "json");
        int rowNumber = 1;
        for (File tagFile : tagFiles) {
            addLabel(tagFile.getName().replace(".json", ""), rowNumber++);
            TagList list = TagList.fromFile(tagFile);
            for (String tag : list.getTags()) {
                addButton(createTagButton(tag), rowNumber++);
            }
            addGroupEditButtons(list, rowNumber++);
        }

        addLabel("Quick tag options", rowNumber++);
        addNewGroupButton(rowNumber++);
        addHidePanelButton(rowNumber++);
        addBottomSpacer(rowNumber);
    }

    private void executeTagAction(String tag) {
        ImageInstance image = MainWindow.getInstance().getSelectedImage();
        if (! image.isEmpty()) {
            File tagFile = new File(image.getImageFile().getParentFile(),
                                    FilenameUtils.getBaseName(image.getImageFile().getName())+".ice");
            TagList imageTags = TagList.fromFile(tagFile);
            if (imageTags.hasTag(tag)) {
                imageTags.remove(tag);
            }
            else {
                imageTags.add(tag);
            }
            imageTags.save();
            ImageViewerExtensionManager.getInstance().imageSelected(image);
        }
    }

    private void addLabel(String text, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 3;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(12,SideMargin,0,SideMargin);
        JLabel label = new JLabel(" " + text);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(panelWidth,RowHeight));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setOpaque(true);
        label.setBackground(LookAndFeelManager.getLafColor("TextArea.selectionBackground", Color.BLUE));
        label.setForeground(LookAndFeelManager.getLafColor("TextArea.selectionForeground", Color.WHITE));
        add(label, gbc);
    }

    private void addSpacer(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 3;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,SideMargin,0,SideMargin);
        JLabel label = new JLabel(" ");
        label.setOpaque(true);
        add(label, gbc);
    }

    private void addButton(JButton button, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 3;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,SideMargin,0,SideMargin);
        add(button, gbc);
    }

    private void addGroupEditButtons(TagList list, int row) {
        JButton button = createButton("");
        button.setIcon(new ImageIcon(iconAddTag, "Add tag"));
        button.setPreferredSize(new Dimension(panelWidth/3, RowHeight));
        button.addActionListener(e -> addNewTag(list));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, SideMargin, 12, 0);
        add(button, gbc);

        button = createButton("");
        button.setIcon(new ImageIcon(iconEditTagGroup, "Edit tag group"));
        button.setPreferredSize(new Dimension(panelWidth/3, RowHeight));
        button.addActionListener(e -> editTagGroup(list));
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 12, 0);
        add(button, gbc);

        button = createButton("");
        button.setIcon(new ImageIcon(iconRemoveTagGroup, "Remove tag group"));
        button.setPreferredSize(new Dimension(panelWidth/3, RowHeight));
        button.addActionListener(e -> removeTagGroup(list));
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 12, SideMargin);
        add(button, gbc);
    }

    private void addNewGroupButton(int row) {
        JButton button = createButton("New group...");
        button.addActionListener(e -> addNewGroup());
        addButton(button, row);
    }

    private void removeTagGroup(TagList list) {
        if (JOptionPane.showConfirmDialog(MainWindow.getInstance(), "Really remove this tag group?") == JOptionPane.YES_OPTION) {
            list.getPersistenceFile().delete();
            reset();
        }
    }

    private void addHidePanelButton(int row) {
        JButton button = createButton("Hide quick tags");
        button.addActionListener(e -> hidePanel());
        addButton(button, row);
    }

    private void addBottomSpacer(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 2;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 2;
        JLabel label = new JLabel("");
        add(label, gbc);
    }

    private JButton createTagButton(String label) {
        JButton button = createButton(label);
        button.addActionListener(e -> executeTagAction(label));
        return button;
    }

    private JButton createButton(String label) {
        JButton button = new JButton(label);
        button.setPreferredSize(new Dimension(panelWidth, RowHeight));
        return button;
    }

    private void editTagGroup(TagList list) {
        TagListEditDialog dialog = new TagListEditDialog("Edit quick tag group", list);
        dialog.setVisible(true);
        if (dialog.wasOkayed()) {
            list.clear();
            list.addAll(dialog.getModifiedTagList());
            list.save();
            reset();
        }
    }

    private void hidePanel() {
        //noinspection unchecked
        ComboProperty<String> prop = (ComboProperty<String>)AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelPositionProp);
        if (prop != null) {
            prop.setSelectedIndex(0);
            ReloadUIAction.getInstance().actionPerformed(null); // overkill? we just want to hide the panel...
            MainWindow.getInstance().showMessageDialog("Quick tags hidden",
                                                       "You can re-enable the quick tags panel in application settings.");
        }
    }

    private void addNewGroup() {
        String name = JOptionPane.showInputDialog(MainWindow.getInstance(), "Enter new group name:");
        if (name != null) {
            File tagFile = new File(tagDir, name + ".json");
            if (tagFile.exists()) {
                MainWindow.getInstance().showMessageDialog("Name in use", "There is already a group with that name.");
                return;
            }
            TagList newList = TagList.fromFile(tagFile);
            newList.save(); // create the empty file
            reset(); // reload everything
        }
    }

    private void addNewTag(TagList list) {
        String input = JOptionPane.showInputDialog(MainWindow.getInstance(), "Enter new tag:");
        if (input != null) {
            list.add(input);
            list.save();
            reset();
        }
    }
}
