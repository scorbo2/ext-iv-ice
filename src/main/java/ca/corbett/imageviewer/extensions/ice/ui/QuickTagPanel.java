package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.ScrollUtil;
import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.ScrollUtil;
import ca.corbett.extras.TextInputDialog;
import ca.corbett.extras.actionpanel.ActionComponentType;
import ca.corbett.extras.actionpanel.ActionPanel;
import ca.corbett.extras.actionpanel.ColorOptions;
import ca.corbett.extras.actionpanel.ColorTheme;
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
import ca.corbett.imageviewer.extensions.ice.ui.dialogs.QuickTagSourceDialog;
import ca.corbett.imageviewer.extensions.ice.ui.formfield.TagNameValidator;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;
import org.apache.commons.io.FilenameUtils;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorUIResource;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
 * <b>Persistence:</b> Quick tag groups are persisted in the "quickTags" directory
 * located in the application settings directory. Subdirectories are created within that directory
 * for each source, and the tag groups for each source are persisted within their respective subdirectories.
 * This allows users to maintain separate sets of quick tags for different contexts
 * (different types of images or projects, for example).
 * </p>
 * <p>
 * The left panel and the right panel store their current visibility state, position, and
 * contents separately, so they can be automatically restored when the application is restarted.
 * Hiding the quick tags panel(s) in application settings does not remove any configured tag groups!
 * You can re-enable the panel(s) at any time to regain access to your quick tags.
 * </p>
 * <p>
 * <b>What if the current image already has the selected tag?</b> The tag buttons on this panel
 * act as a toggle. If the selected tag is not present in the image's tag list, it is added.
 * If the tag is already present, it is removed. You can immediately see the results of the operation
 * in the tag preview pane, which by default is located at the bottom of the main image view.
 * Alternatively, you can press Ctrl+G (or whatever you have mapped that shortcut to) in order
 * to bring up the tag dialog and view/edit the tag list for the current image.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class QuickTagPanel extends JPanel {

    private static final Logger log = Logger.getLogger(QuickTagPanel.class.getName());

    public static final String DEFAULT_SOURCE_NAME = "(default)";
    public static final String OPTIONS_GROUP = "Quick tag options";

    // Size for all icons in our ActionPanel:
    private static final int iconSize = 18;

    private int preferredPanelWidth = 200; // customized via AppConfig

    private ColorTheme appliedTheme; // null = no theme applied, use system defaults
    private final ChangeListener lafListener;
    private final ImageViewerExtension.ExtraPanelPosition position;
    private final ActionPanel actionPanel;
    private final File sourceRootDir;
    private final Map<String, TagList> tagListsByName; // group name -> tag list for that group
    private File sourceDir;
    private String currentSource;

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
        this.tagListsByName = new HashMap<>();
        this.actionPanel = buildActionPanel();

        refreshPreferredWidth();
        setLayout(new BorderLayout());
        JScrollPane scrollPane = ScrollUtil.buildScrollPane(actionPanel, 10);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
        sourceRootDir = new File(Version.SETTINGS_DIR, "quickTags");
        if (!sourceRootDir.exists()) {
            sourceRootDir.mkdirs();
        }

        reset(); // Force a load of our contents

        // Set up a Look and Feel change listener so we can apply our workaround:
        lafListener = e -> applyLafWorkaround();
        LookAndFeelManager.addChangeListener(lafListener);

        // And force the workaround here for immediate effect:
        applyLafWorkaround();
    }

    /**
     * Looks up the currently-configured preferred width for quick tag panels
     * from application settings and updates the instance variable.
     */
    public void refreshPreferredWidth() {
        IntegerProperty prop = (IntegerProperty)AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelWidthProp);
        preferredPanelWidth = prop == null ? 200 : prop.getValue();
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
        tagListsByName.clear();
        actionPanel.setAutoRebuildEnabled(false); // Otherwise each add will trigger an ActionPanel rebuild
        try {
            actionPanel.clear(true); // nuke everything; we'll rebuild from scratch
            List<File> tagFiles = FileSystemUtil.findFiles(sourceDir, false, "tag");
            for (File tagFile : tagFiles) {
                String groupName = tagFile.getName().replace(".tag", ""); // TODO unsafe...
                TagList list = TagList.fromFile(tagFile);
                tagListsByName.put(groupName.toLowerCase(), list);
                for (String tag : list.getTags()) {
                    actionPanel.add(groupName, new TagAction(tag));
                }
                if (list.isEmpty()) {
                    // Add a null action so the empty group gets created and displayed:
                    actionPanel.add(groupName, (EnhancedAction)null);
                }
            }

            // Add the options group at the end, with actions for managing the quick tag groups and sources:
            actionPanel.add(OPTIONS_GROUP, new AddGroupAction());
            actionPanel.add(OPTIONS_GROUP, new ChangeSourceAction());
            actionPanel.add(OPTIONS_GROUP, new HidePanelAction());
        }
        finally {
            // Re-enabling auto-rebuild will trigger an immediate rebuild with our changes above:
            actionPanel.setAutoRebuildEnabled(true);
        }

        // Force a repaint to display the changes:
        this.invalidate();
        this.revalidate();
        this.repaint();
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

    /**
     * Invoked internally to build and return an ActionPanel with all
     * the options and configuration set the way we want for our quick tag panels.
     */
    private ActionPanel buildActionPanel() {
        // This is very weird, but calling setPreferredSize() on the ActionPanel itself
        // prevents the scrollbar from ever showing up, even when the parent container is too
        // small to show all contents. The only way I've found to make this work is
        // to override ActionPanel itself and return the preferred size from there.
        // All of this, by the way, is just so we can set our preferred width. Sigh.
        ActionPanel actionPanel = new ActionPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension superPref = super.getPreferredSize();
                return new Dimension(preferredPanelWidth, superPref.height);
            }
        };

        // Now we can customize ActionPanel to get it to look the way we want it to.
        actionPanel.setHeaderIconSize(iconSize);
        actionPanel.getToolBarOptions().setIconSize(iconSize);
        actionPanel.setActionComponentType(ActionComponentType.BUTTONS);
        actionPanel.getActionTrayMargins().setAll(0); // no gaps between anything, no margins either
        actionPanel.getToolBarMargins().setAll(0); // This should be redundant in Stretch mode.
        actionPanel.getActionGroupMargins().setRight(18); // leave room for scrollbar on the right
        actionPanel.setButtonPadding(4); // Give the buttons just a bit more padding than the default 2 pixels.
        actionPanel.addGroupRenamedListener((a, o, n) -> groupRenamed(o, n));
        actionPanel.addGroupRemovedListener((a, g) -> groupRemoved(g));
        actionPanel.addGroupReorderedListener((a, g) -> groupReordered(g));
        
        actionPanel.getExpandCollapseOptions().setAllowHeaderDoubleClick(true); // convenient!
        actionPanel.setToolBarEnabled(true);
        actionPanel.getToolBarOptions().addExcludedGroup(OPTIONS_GROUP); // Don't add toolbar for our options group
        actionPanel.getToolBarOptions().setEditIcon(
                new ImageIcon(ImageViewerResources.getIconImageSetEdit(ImageViewerResources.NATIVE_ICON_SIZE)));

        // We have to register our "add item" action to ActionPanel, so it will show up
        // in the toolbar.
        actionPanel.getToolBarOptions().setAllowItemAdd(true);
        actionPanel.getToolBarOptions().setNewActionSupplier((a, g) -> addTag(g));

        // Set custom color theme if user has picked one in AppConfig:
        // (otherwise, we'll let the Look and Feel decide all the colors)
        ColorTheme colorTheme = AppConfig.getInstance().getActionPanelTheme();
        if (colorTheme != null) {
            appliedTheme = colorTheme;
            actionPanel.getColorOptions().setFromTheme(colorTheme);

            // Tweak it just a little - I want the action tray background to be the same as
            // the panel background. Otherwise, it looks odd with our action buttons:
            ColorOptions options = actionPanel.getColorOptions();
            options.setActionBackground(AppConfig.getInstance().getDefaultBackground());
            options.setToolBarButtonBackground(options.getActionButtonBackground());
        }
        else {
            appliedTheme = null;
            actionPanel.getColorOptions().useSystemDefaults();
        }

        return actionPanel;
    }

    /**
     * Works around an unfortunate bug in swing-extras regarding button colors in certain Look and Feels.
     * This bug was not fixed before swing-extras 2.8 was released :(
     */
    public void applyLafWorkaround() {
        // Work around an unfortunate bug in the swing-extras library
        // where button borders are set to a very unpleasant default color:
        if (appliedTheme != null) {
            Color borderColor = switch (appliedTheme) {
                case DEFAULT, MATRIX, DARK, ICE -> Color.DARK_GRAY;
                case LIGHT, GOT_THE_BLUES, SHADES_OF_GRAY -> Color.GRAY;
                case HOT_DOG_STAND -> Color.YELLOW; // because hot dog stands are ugly
            };
            UIManager.put("Button.default.startBorderColor", new ColorUIResource(borderColor));
            reset(); // seems like overkill but a simple redraw won't hack it here.

            // This is what I want to do instead of reset(),
            // but it won't work because of the way ActionPanel builds its buttons:
            //SwingUtilities.updateComponentTreeUI(actionPanel);

            invalidate();
            validate();
            repaint();
        }
    }

    /**
     * When a group is renamed, we need to also ensure the persistence file is renamed accordingly,
     * and that our internal map is updated.
     */
    private void groupRenamed(String oldName, String newName) {
        TagList list = tagListsByName.remove(oldName.toLowerCase());
        if (list != null) {
            tagListsByName.put(newName.toLowerCase(), list);
            try {
                File oldFile = list.getPersistenceFile();
                File newFile = new File(oldFile.getParentFile(), newName + ".tag");
                Files.move(oldFile.toPath(), newFile.toPath());
                list.setPersistenceFile(newFile);
                list.save(); // this might seem redundant, but user can rename + reorder in one step, so let's be sure.
            }
            catch (IOException ioe) {
                // Log and display the error:
                log.log(Level.SEVERE, "Problem renaming tag group file: " + ioe.getMessage(), ioe);
                MainWindow.getInstance().showMessageDialog("Error",
                                                           "There was a problem renaming the tag group file. See log for details.");

                // Now revert our map change:
                tagListsByName.remove(newName.toLowerCase());
                tagListsByName.put(oldName.toLowerCase(), list);
            }
        }
    }

    /**
     * When a group is removed, we need to delete the corresponding persistence file
     * and remove the entry from our internal map.
     */
    private void groupRemoved(String groupName) {
        TagList list = tagListsByName.remove(groupName.toLowerCase());
        if (list != null) {
            File file = list.getPersistenceFile();
            if (file.exists()) {
                if (!file.delete()) {
                    log.warning("Unable to delete tag group file: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * When the items within a group are reordered, we need to update the order in the corresponding TagList
     * and persist the changes. The ActionPanel doesn't know anything about our TagList objects,
     * so we have to manually sync the order from the ActionPanel back to our TagList whenever this happens.
     */
    private void groupReordered(String groupName) {
        TagList list = tagListsByName.get(groupName.toLowerCase());
        List<String> newTags = new ArrayList<>();
        if (list != null) {
            for (EnhancedAction action : actionPanel.getActionsForGroup(groupName)) {
                if (action instanceof TagAction tagAction) {
                    newTags.add(tagAction.tag);
                }
            }
            list.clear();
            list.addAll(newTags);
            list.save();
        }
    }

    /**
     * Prompts for a new tag name, adds it to the specified group, and returns
     * a new TagAction for that tag so it can be added to the ActionPanel.
     */
    private EnhancedAction addTag(String groupName) {
        TagList tagList = tagListsByName.get(groupName.toLowerCase());
        if (tagList == null) {
            log.warning("Unable to find tag list for group: " + groupName + ", cannot add new tag.");
            return null;
        }
        TextInputDialog dialog = new TextInputDialog(MainWindow.getInstance(), "New tag");
        dialog.addValidator(new TagNameValidator(tagList));
        dialog.setPrompt("Enter new tag:");
        dialog.setVisible(true);
        String input = dialog.getResult();
        if (input != null) {
            String normalizedTag = TagList.stripTag(input); // don't show it as-typed, show the normalized version
            tagList.add(normalizedTag);
            tagList.save(); // commit the changes immediately
            return new TagAction(normalizedTag);
        }
        return null;
    }

    /**
     * A simple Action that applies a given tag to the current image when triggered.
     * This acts as a toggle - if the given tag is already present in the selected
     * image, it is removed. Otherwise, it is added. The tag index is updated
     * immediately, and the tag preview pane is refreshed to reflect the change.
     */
    private static class TagAction extends EnhancedAction {
        private final String tag;

        public TagAction(String tag) {
            super(tag);
            this.tag = tag;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ImageInstance image = MainWindow.getInstance().getSelectedImage();
            if (!image.isEmpty()) {
                File tagFile = new File(image.getImageFile().getParentFile(),
                                        FilenameUtils.getBaseName(image.getImageFile().getName()) + ".ice");
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
    }

    /**
     * An Action for adding a new, empty ActionGroup to the current quick tag source.
     */
    private class AddGroupAction extends EnhancedAction {

        public AddGroupAction() {
            super("New group...");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String name = JOptionPane.showInputDialog(MainWindow.getInstance(), "Enter new group name:");
            if (name != null) {
                File tagFile = new File(sourceDir, name + ".tag");
                if (tagFile.exists()) {
                    MainWindow.getInstance()
                              .showMessageDialog("Name in use", "There is already a group with that name.");
                    return;
                }
                TagList newList = TagList.fromFile(tagFile);
                newList.save(); // create the empty file
                reset(); // reload everything
            }
        }
    }

    /**
     * An Action for selecting a different quick tag source. This launches the QuickTagSourceDialog,
     * and if the user selects a different source and confirms, the panel is reset to show
     * the contents of the newly selected source. The selected source is also persisted in application settings
     * so that it is restored on next launch.
     */
    private class ChangeSourceAction extends EnhancedAction {

        public ChangeSourceAction() {
            super("Quick tag source");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            QuickTagSourceDialog dialog = new QuickTagSourceDialog("Quick tag source", currentSource);
            dialog.setVisible(true);
            if (dialog.wasOkayed()) {
                if (dialog.wasSourceChanged()) {
                    log.info("Switching to quick tag source: " + dialog.getSelectedSourceName());
                    currentSource = dialog.getSelectedSourceName();
                    setSourceInProperties(currentSource);
                    reset();
                }
            }
        }
    }

    /**
     * An Action to hide the quick tag panel(s) and update application settings accordingly.
     * The main UI is refreshed so that the change is immediately visible. Users must visit
     * the application properties dialog and manually re-enable the quick tag panel(s) if
     * they want to get them back, but no data is lost as a result of this operation.
     */
    private static class HidePanelAction extends EnhancedAction {

        public HidePanelAction() {
            super("Hide quick tags");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
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
    }
}
