package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.extras.properties.ShortTextProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ImageViewerResources;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.QuickTagGroupEditDialog;
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.QuickTagSourceDialog;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;
import org.apache.commons.io.FilenameUtils;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The QuickTagPanel presents a configurable list of commonly-used tags that can be applied to the
 * currently selected image with a single button click. The user can opt to place this panel
 * either to the left of the main image, or to the right of it, or have one QuickTagPanel in
 * each of those positions. The panel(s) can also be hidden entirely in application settings.
 * <p>
 * <b>Tag groups:</b> Tags can be grouped together into named groups, which are presented
 * in separate collapsible sections in the panel. Each group offers options for ordering the
 * tags within it, as well as renaming or deleting the group itself.
 * </p>
 * <p>
 * <b>Persistence:</b> Quick tag groups are persisted as JSON files in the
 * "quickTags" directory located in the application settings directory. Each source has its own
 * subdirectory within that directory, allowing users to maintain separate sets of quick tags
 * for different contexts (different types of images or projects, for example). Each panel's
 * visibility state, position, and contents are automatically restored when the application is restarted.
 * Hiding the quick tags panel in application settings does not remove any configured tag groups!
 * You can re-enable the panel at any time to regain access to your quick tags.
 * </p>
 * <p>
 * <b>What if the current image already has the selected tag?</b> The tag buttons on this panel
 * act as a toggle. If the selected tag is not present in the image's tag list, it is added.
 * If the tag is already present, it is removed. You can immediately see the results of the operation
 * in the tag preview pane, which by default is located at the bottom of the main image view.
 * Alternatively, you can press Ctrl+G (or whatever you have mapped that shortcut to) in order
 * to bring up the tag dialog and view/edit the tag list for the current image.
 * </p>
 * <p>
 * Minor TODO here: now that the application supports custom color schemes (as of 3.0), we should
 * either use the options set by the application, or add our own customization options for the
 * user to modify our colors. Currently, we always use colors from the current Look and Feel.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class QuickTagPanel extends JPanel {

    private static final Logger log = Logger.getLogger(QuickTagPanel.class.getName());

    public static final String DEFAULT_SOURCE_NAME = "(default)";

    public static final int SideMargin = 16;
    public static final int RowHeight = 25;

    private final ImageViewerExtension.ExtraPanelPosition position;
    private final File sourceRootDir;
    private File sourceDir;
    private int panelWidth;
    private final BufferedImage iconAddTag;
    private final BufferedImage iconEditTagGroup;
    private final BufferedImage iconRemoveTagGroup;
    private final BufferedImage iconExpand;
    private final BufferedImage iconContract;

    private String currentSource;
    private final Map<String, Boolean> isExpandedMap = new HashMap<>();

    /**
     * Creates a QuickTagPanel for the specified position.
     * Only left and right positions are supported! All other positions
     * will result in an IllegalArgumentException.
     * <p>
     * The position is a required parameter because the panel's contents are
     * persisted according to its position. This allows the "both left and right position"
     * option to function correctly, with each panel maintaining its own independent
     * set of quick tag groups.
     * </p>
     *
     * @param position The desired position, either Left or Right.
     */
    public QuickTagPanel(ImageViewerExtension.ExtraPanelPosition position) {
        if (position != ImageViewerExtension.ExtraPanelPosition.Left
                && position != ImageViewerExtension.ExtraPanelPosition.Right) {
            log.log(Level.SEVERE, "QuickTagPanel must be created for left or right position, got: " + position);
            throw new IllegalArgumentException("QuickTagPanel is only supported in left or right position.");
        }
        this.position = position;

        setLayout(new GridBagLayout());
        sourceRootDir = new File(Version.SETTINGS_DIR, "quickTags");
        if (!sourceRootDir.exists()) {
            sourceRootDir.mkdirs();
        }
        final int iconSize = 18; // not configurable
        iconAddTag = ImageViewerResources.getIconPlus(iconSize);
        iconEditTagGroup = ImageViewerResources.getIconImageSetEdit(iconSize);
        iconRemoveTagGroup = ImageViewerResources.getIconDelete(iconSize);
        iconExpand = ImageViewerResources.getIconZoomIn(iconSize);
        iconContract = ImageViewerResources.getIconZoomOut(iconSize);

        reset(); // Force a load of our contents
    }

    /**
     * Returns the name of the currently selected quick tag source for this panel.
     */
    public String getSource() {
        return currentSource;
    }

    /**
     * Reloads the panel contents from the currently selected quick tag source.
     */
    public void reset() {
        currentSource = getSourceFromProperties();
        sourceDir = DEFAULT_SOURCE_NAME.equals(currentSource)
                ? sourceRootDir
                : new File(sourceRootDir, currentSource);
        if (!sourceDir.exists()) {
            sourceDir.mkdirs();
        }
        IntegerProperty prop = (IntegerProperty)AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelWidthProp);
        panelWidth = prop == null ? 200 : prop.getValue();
        removeAll();
        List<File> tagFiles = FileSystemUtil.findFiles(sourceDir, false, "json");
        int rowNumber = 1;
        for (File tagFile : tagFiles) {
            String groupName = tagFile.getName().replace(".json", "");
            addLabel(groupName, rowNumber++);
            if (Boolean.TRUE.equals(isExpandedMap.get(groupName))) {
                TagList list = TagList.fromFile(tagFile);
                for (String tag : list.getTags()) {
                    addButton(createTagButton(tag), rowNumber++);
                }
                addGroupEditButtons(groupName, list, rowNumber++);
            }
        }

        addNonExpandableLabel("Quick tag options", rowNumber++);
        addNewGroupButton(rowNumber++);
        addSourcesButton(rowNumber++);
        addHidePanelButton(rowNumber++);
        addBottomSpacer(rowNumber);

        // Force a repaint to display the changes:
        this.invalidate();
        this.revalidate();
        this.repaint();
    }

    /**
     * Applies the specified tag to the current image - this means adding it to the image's
     * tag list if it is not already present, or removing it if it is. Remember that tags are
     * case-insensitive, so "tag", "Tag", and "TAG" are all considered the same tag.
     *
     * @param tag The tag in question.
     */
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
            TagIndex.getInstance().addOrUpdateEntry(image.getImageFile(), tagFile);
            ImageViewerExtensionManager.getInstance().imageSelected(image);
        }
    }

    private void addNonExpandableLabel(String text, int row) {
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

    private void addLabel(String text, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 3;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(12,SideMargin,0,SideMargin);
        final HeaderLabel headerLabel = new HeaderLabel(text, panelWidth);
        headerLabel.addButtonListener(e -> {
            isExpandedMap.put(text, !headerLabel.isExpanded());
            reset();
        });
        isExpandedMap.putIfAbsent(text, true);
        if (Boolean.FALSE.equals(isExpandedMap.get(text))) {
            headerLabel.setExpanded(false);
        }
        add(headerLabel, gbc);
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

    private void addGroupEditButtons(String groupName, TagList list, int row) {
        JButton button = createButton("");
        button.setIcon(new ImageIcon(iconAddTag, "Add tag"));
        button.setToolTipText("Add tag");
        button.setPreferredSize(new Dimension(panelWidth/3, RowHeight));
        button.addActionListener(e -> addNewTag(list));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.333;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, SideMargin, 12, 0);
        add(button, gbc);

        button = createButton("");
        button.setIcon(new ImageIcon(iconEditTagGroup, "Edit tag group"));
        button.setToolTipText("Edit this tag group");
        button.setPreferredSize(new Dimension(panelWidth/3, RowHeight));
        button.addActionListener(e -> editTagGroup(groupName, list));
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 12, 0);
        add(button, gbc);

        button = createButton("");
        button.setIcon(new ImageIcon(iconRemoveTagGroup, "Remove tag group"));
        button.setToolTipText("Remove this tag group");
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

    private void addSourcesButton(int row) {
        JButton button = createButton("Quick tag source");
        button.addActionListener(e -> selectSource());
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
        gbc.insets = new Insets(SideMargin, 0,0,0);
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

    private void editTagGroup(String groupName, TagList list) {
        QuickTagGroupEditDialog dialog = new QuickTagGroupEditDialog("Edit quick tag group", groupName, list);
        dialog.setVisible(true);
        if (dialog.wasOkayed()) {
            list.clear();
            list.addAll(dialog.getModifiedTagList());

            // The user may have renamed this tag list, in which case we have to update the save location:
            if (dialog.groupWasRenamed()) {
                File originalFile = list.getPersistenceFile();
                File target = new File(list.getPersistenceFile().getParentFile(), dialog.getModifiedGroupName() + ".json");
                list.setPersistenceFile(target);

                // Also remove the old one:
                originalFile.delete();
            }

            list.save();
            reset();
        }
    }

    /**
     * Hides all QuickTagPanels and silently updates application preferences to keep them hidden.
     * Nothing is deleted or lost as a result of this operation! The panels can be re-enabled
     * at any time in application settings.
     */
    private void hidePanel() {
        // Get the property that controls quick tag panel position:
        AbstractProperty prop = AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelPositionProp);

        if (prop instanceof ComboProperty<?> comboProp) {
            comboProp.setSelectedIndex(0); // "None" position
            AppConfig.getInstance().save(); // force silent save to persist this immediately

            // It may seem like overkill to reload the entire UI just to hide these panels,
            // but the main image view needs to be laid out again as a result of this change.
            ReloadUIAction.getInstance().actionPerformed(null);

            // Explain to the user what just happened and how to reverse it later:
            MainWindow.getInstance().showMessageDialog("Quick tags hidden",
                                                       "You can re-enable the quick tag panel(s) in application settings.");
        }
    }

    private void addNewGroup() {
        String name = JOptionPane.showInputDialog(MainWindow.getInstance(), "Enter new group name:");
        if (name != null) {
            File tagFile = new File(sourceDir, name + ".json");
            if (tagFile.exists()) {
                MainWindow.getInstance().showMessageDialog("Name in use", "There is already a group with that name.");
                return;
            }
            TagList newList = TagList.fromFile(tagFile);
            newList.save(); // create the empty file
            reset(); // reload everything
        }
    }

    private void selectSource() {
        QuickTagSourceDialog dialog = new QuickTagSourceDialog("Quick tag source", currentSource);
        dialog.setVisible(true);
        if (dialog.wasOkayed()) {
            if (dialog.wasSourceChanged()) {
                log.info("Switching to quick tag source: "+dialog.getSelectedSourceName());
                currentSource = dialog.getSelectedSourceName();
                setSourceInProperties(currentSource);
                isExpandedMap.clear();
                reset();
            }
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

    /**
     * Represents a header label with a collapse/expand button.
     * When collapsed, only the header label and the expand button are shown.
     * This is great for having a potentially large number of tag groups without requiring
     * a lot of vertical scrolling to see them all.
     */
    private class HeaderLabel extends JPanel {
        private final String labelText;
        private final JButton btnExpander;
        private boolean isExpanded;

        public HeaderLabel(String labelText, int panelWidth) {
            super(new BorderLayout());
            this.labelText = labelText;
            JLabel label = new JLabel(" " + labelText);
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(panelWidth,RowHeight));
            label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
            label.setOpaque(true);
            label.setBackground(LookAndFeelManager.getLafColor("TextArea.selectionBackground", Color.BLUE));
            label.setForeground(LookAndFeelManager.getLafColor("TextArea.selectionForeground", Color.WHITE));
            add(label, BorderLayout.CENTER);

            btnExpander = new JButton(new ImageIcon(iconContract));
            btnExpander.setPreferredSize(new Dimension(RowHeight, RowHeight));
            btnExpander.setBorder(null);
            btnExpander.setBackground(LookAndFeelManager.getLafColor("TextArea.selectionBackground", Color.BLUE));
            isExpanded = true;
            add(btnExpander, BorderLayout.EAST);
        }

        public boolean isExpanded() {
            return isExpanded;
        }

        public void setExpanded(boolean isExpanded) {
            if (this.isExpanded == isExpanded) {
                return; // ignore no-op
            }
            this.isExpanded = isExpanded;
            if (isExpanded) {
                btnExpander.setIcon(new ImageIcon(iconContract));
            }
            else {
                btnExpander.setIcon(new ImageIcon(iconExpand));
            }
        }

        public String getLabelText() {
            return labelText;
        }

        public void addButtonListener(ActionListener listener) {
            btnExpander.addActionListener(listener);
        }
    }

    /**
     * If our source is changed, we need to update the appropriate property
     * in application settings and persist it so that it is restored on next launch.
     *
     * @param source The name of the new source.
     */
    private void setSourceInProperties(String source) {
        String propName = position == ImageViewerExtension.ExtraPanelPosition.Left
                ? IceExtension.quickTagLeftSourceProp
                : IceExtension.quickTagRightSourceProp;
        AbstractProperty prop = AppConfig.getInstance().getPropertiesManager().getProperty(propName);
        if (!(prop instanceof ShortTextProperty shortTextProp)) {
            log.warning("QuickTagPanel: Unable to find source property, unable to save source preference.");
            return;
        }
        shortTextProp.setValue(source);
        AppConfig.getInstance().save(); // force silent save to persist this immediately
    }

    /**
     * Returns the persisted name of our tag source from application settings.
     * If there is no persisted source for our position, the default source name is returned.
     *
     * @return A tag source name for this panel.
     */
    private String getSourceFromProperties() {
        String propName = position == ImageViewerExtension.ExtraPanelPosition.Left
                ? IceExtension.quickTagLeftSourceProp
                : IceExtension.quickTagRightSourceProp;
        AbstractProperty prop = AppConfig.getInstance().getPropertiesManager().getProperty(propName);
        if (!(prop instanceof ShortTextProperty shortTextProp)) {
            log.warning("QuickTagPanel: Unable to find source property, reverting to default source.");
            return DEFAULT_SOURCE_NAME;
        }
        return shortTextProp.getValue();
    }
}
