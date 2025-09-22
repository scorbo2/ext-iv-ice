package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.RedispatchingMouseAdapter;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ImageOperation;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.ice.actions.ScanDirAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagMultipleImagesAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagSingleImageAction;
import ca.corbett.imageviewer.extensions.ice.actions.SearchAction;
import ca.corbett.imageviewer.extensions.ice.ui.TagPanel;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ThumbPanel;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IceExtension extends ImageViewerExtension {

    private static final Logger log = Logger.getLogger(IceExtension.class.getName());

    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/ice/extInfo.json";
    private final AppExtensionInfo extInfo;

    private static final String[] validPositions = {"Above main image", "Below main image"};
    private static final String fontSizePropName = "Thumbnails.Companion files.linkFontSize";

    private TagPanel tagPanel;

    public IceExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), extInfoLocation);
        if (extInfo == null) {
            throw new RuntimeException("IceExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> list = new ArrayList<>();
        list.add(new BooleanProperty("ICE.General.showTagPanel", "Show tag panel on main image tab", true));
        list.add(new ComboProperty<>("ICE.General.position", "Tag panel position:", Arrays.asList(validPositions), 1, false));
        list.add(new IntegerProperty(fontSizePropName, "Hyperlink font size", 10, 8, 16, 1));
        list.add(new BooleanProperty(TagIndex.PROP_NAME, "Enable tag index for faster searches", true));
        return list;
    }

    @Override
    public void onActivate() {
        TagIndex.getInstance().load();
    }

    @Override
    public void onDeactivate() {
        TagIndex.getInstance().save();
    }

    private ExtraPanelPosition getConfiguredPanelPosition() {
        //noinspection unchecked
        ComboProperty<String> prop = (ComboProperty<String>)AppConfig.getInstance().getPropertiesManager().getProperty("ICE.General.position");
        if (prop == null) {
            return ExtraPanelPosition.Bottom; // arbitrary default, should really log the failure though
        }
        if (prop.getSelectedIndex() == 0) {
            return ExtraPanelPosition.Top;
        }
        return ExtraPanelPosition.Bottom;
    }

    @Override
    public JComponent getExtraPanelComponent(ExtraPanelPosition position) {
        if (! ((BooleanProperty)AppConfig.getInstance().getPropertiesManager().getProperty("ICE.General.showTagPanel")).getValue()) {
            return null;
        }
        if (position == getConfiguredPanelPosition()) {
            tagPanel = new TagPanel();
            return tagPanel;
        }
        return null;
    }

    @Override
    public List<JMenu> getTopLevelMenus(MainWindow.BrowseMode browseMode) {
        JMenu iceMenu = new JMenu("ICE");
        iceMenu.setMnemonic(KeyEvent.VK_I);

        JMenuItem searchItem = new JMenuItem(new SearchAction());
        iceMenu.add(searchItem);

        JMenuItem tagSingleImageItem = new JMenuItem(new TagSingleImageAction());
        tagSingleImageItem.setMnemonic(KeyEvent.VK_G);
        tagSingleImageItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        iceMenu.add(tagSingleImageItem);

        if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM) {
            JMenuItem tagMultiImagesItem = new JMenuItem(new TagMultipleImagesAction("Tag images..."));
            iceMenu.add(tagMultiImagesItem);

            if (TagIndex.isEnabled()) {
                JMenuItem scanDirItem = new JMenuItem(new ScanDirAction("Tag scan: current directory", false));
                iceMenu.add(scanDirItem);

                JMenuItem scanDirItemRecursive = new JMenuItem(
                        new ScanDirAction("Tag scan: current directory recursively", true));
                iceMenu.add(scanDirItemRecursive);
            }
        }

        return List.of(iceMenu);
    }

    /**
     * I wanted to use ctrl+t for "tag" but it was already in use by the
     * image transform extension (ctrl+t for "transform"). One day I'll
     * make extension keyboard shortcuts user-configurable.
     */
    @Override
    public boolean handleKeyboardShortcut(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_G) {
            if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) > 0) {
                new TagSingleImageAction().actionPerformed(null);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCompanionFile(File candidateFile) {
        // First make sure it's a file that we would work with:
        String name = candidateFile.getName().toLowerCase();
        if (!name.endsWith(".ice")) {
            return false;
        }

        // Now make sure there's an image file with a matching name.
        return getMatchingImageFile(candidateFile) != null;
    }

    /**
     * Given an ice file, return its matching image file, if any.
     * Note that there's a subtle bug here where a single companion file could have multiple
     * matching image files if poor filename discipline is at play. For example:
     * image01.jpg, image01.png, image01.gif, and image01.jpeg could all exist in the same dir.
     * In that case, what file does image01.ice match? The unfortunate answer: the first one
     * that this method finds. Sigh. The only fix for this is to change the approach to
     * companion file naming such that we use the ENTIRE name and not just the base name.
     * In the above example, the companion file(s) would be image01.jpg.ice, image01.png.ice and etc.
     * But that's ugly and I kind of don't want to do it. Right now the workaround is
     * "be smarter about how you name your files".
     */
    public static File getMatchingImageFile(File companionFile) {
        // I hate that this code is case-sensitive...
        String[] imageExtensions = new String[]{"gif", "GIF", "jpg", "JPG", "jpeg", "JPEG", "png", "PNG", "tiff", "bmp"};
        File dir = companionFile.getParentFile();
        String basename = FilenameUtils.getBaseName(companionFile.getName());
        for (String ext : imageExtensions) {
            File test = new File(dir, basename + "." + ext);
            if (test.exists()) {
                return test;
            }
        }
        return null;
    }

    @Override
    public List<File> getCompanionFiles(File imageFile) {
        List<File> companions = new ArrayList<>();

        // Check if a matching .ice file exists in same dir:
        File testFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName())+".ice");
        if (testFile.exists()) {
            companions.add(testFile);
        }

        return companions;
    }

    @Override
    public void imageSelected(ImageInstance selectedImage) {
        if (tagPanel == null) {
            return;
        }

        tagPanel.clearTags();
        if (! selectedImage.isEmpty()) {
            File imageFile = selectedImage.getImageFile();
            if (imageFile != null && imageFile.exists()) {
                File file = new File(imageFile.getParentFile(),
                                     FilenameUtils.getBaseName(imageFile.getName())+".ice");
                if (file.exists()) {
                    tagPanel.setTagList(TagList.fromFile(file));
                }
            }
        }
    }

    /**
     * Overridden here so we can keep our tag index up to date as images are moved, renamed,
     * deleted, copied, or symlinked. Note: this should be handled in postImageOperation, but
     * there's <a href="https://github.com/scorbo2/imageviewer/issues/42">an issue</a> that needs addressed first.
     */
    @Override
    public void preImageOperation(ImageOperation.Type opType, File srcFile, File destFile) {
        switch (opType) {
            case DELETE: TagIndex.getInstance().removeEntry(srcFile); break;

            case MOVE:
                TagIndex.getInstance().removeEntry(srcFile);
                List<File> tagFiles = getCompanionFiles(destFile);
                if (! tagFiles.isEmpty()) {
                    TagIndex.getInstance().addOrUpdateEntry(destFile, tagFiles.get(0)); // there can be only 1
                }
                break;

            case SYMLINK:
            case COPY:
                List<File> copiedTagFiles = getCompanionFiles(destFile);
                if (! copiedTagFiles.isEmpty()) {
                    TagIndex.getInstance().addOrUpdateEntry(destFile, copiedTagFiles.get(0)); // there can be only 1
                }
                break;
        }
    }

    @Override
    public void thumbPanelCreated(ThumbPanel thumbPanel) {
        File srcFile = thumbPanel.getFile();
        if (srcFile == null) {
            return;
        }
        File iceFile = new File(srcFile.getParentFile(), FilenameUtils.getBaseName(srcFile.getName()) + ".ice");
        if (iceFile.exists()) {

            // Assuming there will be other CompanionFileExtensions for different companion file
            // types. It's therefore possible that one of the others has already created the wrapper
            // panel, and in that case we can just use it. If not, we will create it.
            JPanel wrapperPanel = (JPanel)thumbPanel.getExtraProperty("companionFileWrapperPanel");
            if (wrapperPanel == null) {
                wrapperPanel = new JPanel();
                wrapperPanel.addMouseListener(new RedispatchingMouseAdapter());
                wrapperPanel.setBackground(thumbPanel.getBackground());
                thumbPanel.setExtraProperty("companionFileWrapperPanel", wrapperPanel);
                wrapperPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
            }

            if (iceFile.exists()) {
                JLabel iceLabel = createLabel("[ICE]");
                CompanionFileMouseListener listener = new CompanionFileMouseListener(iceFile);
                iceLabel.addMouseListener(listener);
                thumbPanel.setExtraProperty("companionTextFileLabel", iceLabel);
                thumbPanel.setExtraProperty("companionTextFileLabelListener", listener);
                wrapperPanel.add(iceLabel);
            }

            thumbPanel.add(wrapperPanel, BorderLayout.NORTH);
        }
    }

    /**
     * Invoked internally to create the hyperlink label to launch the viewer dialog.
     * The font size for the label is taken from our config property.
     *
     * @param text The text for the label
     * @return A JLabel
     */
    private JLabel createLabel(final String text) {
        IntegerProperty fontSizeProp = (IntegerProperty)AppConfig.getInstance().getPropertiesManager().getProperty(fontSizePropName);
        if (fontSizeProp == null) {
            log.log(Level.SEVERE, "IceExtension: can't find our config property!");
            fontSizeProp = new IntegerProperty(fontSizePropName, "Hyperlink font size", 10, 8, 16, 1);
        }
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setFont(label.getFont().deriveFont((float)fontSizeProp.getValue()));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setForeground(LookAndFeelManager.getLafColor("Component.linkColor", Color.BLUE));
        return label;
    }

}
