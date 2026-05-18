package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.RedispatchingMouseAdapter;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.extras.properties.LabelProperty;
import ca.corbett.extras.properties.LongTextProperty;
import ca.corbett.extras.properties.PasswordProperty;
import ca.corbett.extras.properties.PropertiesManager;
import ca.corbett.extras.properties.ShortTextProperty;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ImageOperation;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.ice.actions.AutoTagAction;
import ca.corbett.imageviewer.extensions.ice.actions.AutoTagBatchAction;
import ca.corbett.imageviewer.extensions.ice.actions.QuickTagToggleLeftAction;
import ca.corbett.imageviewer.extensions.ice.actions.QuickTagToggleLeftRightAction;
import ca.corbett.imageviewer.extensions.ice.actions.QuickTagToggleRightAction;
import ca.corbett.imageviewer.extensions.ice.actions.RandomImageSetAction;
import ca.corbett.imageviewer.extensions.ice.actions.ScanDirAction;
import ca.corbett.imageviewer.extensions.ice.actions.SearchAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagDirStatsAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagMultipleImagesAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagSingleImageAction;
import ca.corbett.imageviewer.extensions.ice.actions.TagStatsAction;
import ca.corbett.imageviewer.extensions.ice.ui.QuickTagPanel;
import ca.corbett.imageviewer.extensions.ice.ui.TagPreviewPanel;
import ca.corbett.imageviewer.extensions.ice.ui.formfield.TagHotkeyProperty;
import ca.corbett.imageviewer.extensions.ice.ui.formfield.UrlValidator;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ThumbPanel;
import ca.corbett.imageviewer.ui.UIReloadable;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is an extension for ImageViewer that provides the ability to "tag" images
 * with short text strings, and then search for images based on those tags.
 * This extension makes heavy use of the new Image Set functionality provided
 * by the ImageViewer 2.2 release.
 * <p>
 *     This extension is a ground-up rewrite of some of the functionality from
 *     the original ICE (Image Classification Engine) application from 2012.
 *     Now in ImageViewer extension form!
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class IceExtension extends ImageViewerExtension implements UIReloadable {

    private static final Logger log = Logger.getLogger(IceExtension.class.getName());

    private static final String requestTemplateLocation = "ca/corbett/imageviewer/extensions/ice/llm/request_template.json";
    private static final String sysPromptTaggedLocation = "ca/corbett/imageviewer/extensions/ice/llm/sys_prompt_tagged.txt";
    private static final String sysPromptUntaggedLocation = "ca/corbett/imageviewer/extensions/ice/llm/sys_prompt_untagged.txt";
    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/ice/extInfo.json";
    public static AppExtensionInfo extInfo;

    private static final String[] validPositionsTagPreviewPanel = {
            "Don't show tag preview",
            "Show above main image",
            "Show below main image"
    };
    private static final String[] validPositionsQuickTagPanel = {
            "Don't show quick tag panel",
            "Left",
            "Right",
            "Both left and right"
    };
    public static final String tagPreviewPanelPositionProp = "ICE.ICE options.tagPreviewPanelPosition";
    public static final String quickTagPanelPositionProp = "ICE.ICE options.quickTagPanelPosition";
    public static final String quickTagPanelWidthProp = "ICE.ICE options.quickTagPanelWidth";
    public static final String fontSizeProp = "Thumbnails.Companion files.linkFontSize";
    public static final String quickTagLeftSourceProp = "Hidden.quickTagsLeft.source";
    public static final String quickTagRightSourceProp = "Hidden.quickTagsRight.source";
    public static final String imageTagShortcutProp = AppConfig.KEYSTROKE_PREFIX + "ICE - General.quickTagPanel";
    public static final String tagHotkeyPropPrefix = AppConfig.KEYSTROKE_PREFIX + "ICE - General.tagHotKey";

    public static final String QUICK_TAG_TOGGLE = AppConfig.KEYSTROKE_PREFIX + "ICE - QuickTag toggles.";
    public static final String quickTagShortcutLeftProp = QUICK_TAG_TOGGLE + "quickTagPanelLeft";
    public static final String quickTagShortcutRightProp = QUICK_TAG_TOGGLE + "quickTagPanelRight";
    public static final String quickTagShortcutLeftRightProp = QUICK_TAG_TOGGLE + "quickTagPanelLeftRight";

    public static final String llmIntroLabelProp = "ICE.Auto-tag.introLabel";
    public static final String llmApiKeyProp = "ICE.Auto-tag.apiKey";
    public static final String llmModelProp = "ICE.Auto-tag.model";
    public static final String llmUrlProp = "ICE.Auto-tag.url";
    public static final String llmTagsProp = "ICE.Auto-tag.tags";
    public static final String autoTagKeyProp = "ICE.Auto-tag.autoTagHotKey";
    public static final String autoTagBatchKeyProp = "ICE.Auto-tag.autoTagBatchHotKey";
    public static final String sysPromptTaggedProp = "ICE.Auto-tag.sysPromptTagged";
    public static final String sysPromptTaglessProp = "ICE.Auto-tag.sysPromptTagless";

    private final List<TagPreviewPanel> tagPreviewPanels = new ArrayList<>();
    private final List<QuickTagPanel> quickTagPanels = new ArrayList<>();

    private final String requestTemplate;
    private String sysPromptTagged;
    private String sysPromptUntagged;

    public IceExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), extInfoLocation);
        if (extInfo == null) {
            throw new RuntimeException("IceExtension: can't parse extInfo.json!");
        }

        // Note: we can't do this in loadJarResources() due to a bug in ExtensionManager.
        //       Turns out loadJarResources() is not invoked until AFTER createConfigProperties().
        //       This is a problem because we want to pass these templates to our AutoTagAction.
        //       Submitted [issue 469](https://github.com/scorbo2/swing-extras/issues/469) in swing-extras
        //       to address this. For now, we have to load everything in our constructor.
        requestTemplate = getTextResource(requestTemplateLocation); // fixed value - never editable by user
        if (requestTemplate == null) {
            log.severe("IceExtension: LLM support is disabled due to missing request template.");
        }
        sysPromptTagged = getTextResource(sysPromptTaggedLocation); // default value can be overridden by user
        sysPromptUntagged = getTextResource(sysPromptUntaggedLocation); // default value can be overridden by user
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public void loadJarResources() {
    }

    /**
     * Returns all the config properties that this extension will use.
     * The parent application will add these to the application config dialog.
     */
    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> list = new ArrayList<>();
        list.add(new ComboProperty<>(tagPreviewPanelPositionProp, "Read-only tag preview:",
                                     Arrays.asList(validPositionsTagPreviewPanel), 2, false));
        list.add(new ComboProperty<>(quickTagPanelPositionProp, "Quick tag position:",
                                     Arrays.asList(validPositionsQuickTagPanel), 1, false));
        list.add(new IntegerProperty(quickTagPanelWidthProp, "Quick tag panel width:", 200, 120, 300, 10));
        list.add(new IntegerProperty(fontSizeProp, "Hyperlink font size", 10, 8, 16, 1));
        list.add(new BooleanProperty(TagIndex.PROP_NAME, "Enable tag index for faster searches", true));
        list.add(new ShortTextProperty(quickTagLeftSourceProp, "quickTagsLeftSource",
                                       QuickTagPanel.DEFAULT_SOURCE_NAME).setExposed(false));
        list.add(new ShortTextProperty(quickTagRightSourceProp, "quickTagsRightSource",
                                       QuickTagPanel.DEFAULT_SOURCE_NAME).setExposed(false));
        list.add(new KeyStrokeProperty(imageTagShortcutProp, "Image tag dialog:",
                                       KeyStrokeManager.parseKeyStroke("Ctrl+G"),
                                       TagSingleImageAction.getInstance())
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES));
        list.add(new KeyStrokeProperty(quickTagShortcutLeftProp, "Toggle left position:",
                                       KeyStrokeManager.parseKeyStroke("ctrl+left"),
                                       QuickTagToggleLeftAction.getInstance())
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                         .setHelpText("Toggles a QuickTag panel in the left position."));
        list.add(new KeyStrokeProperty(quickTagShortcutRightProp, "Toggle right position:",
                                       KeyStrokeManager.parseKeyStroke("ctrl+right"),
                                       QuickTagToggleRightAction.getInstance())
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                         .setHelpText("Toggles a QuickTag panel in the right position."));
        list.add(new KeyStrokeProperty(quickTagShortcutLeftRightProp, "Toggle both positions:",
                                       KeyStrokeManager.parseKeyStroke("ctrl+up"),
                                       QuickTagToggleLeftRightAction.getInstance())
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                         .setHelpText("Toggles a QuickTag panel in both left and right positions."));

        // NEW in 3.3.0 - let's add some LLM connection parameters:
        list.add(new LabelProperty(llmIntroLabelProp, "<html><b>Experimental:</b> you can connect to<br>" +
                "a local or remote LLM to auto-generate<br>" +
                "tags for your images.</html>"));
        ShortTextProperty urlProp = new ShortTextProperty(llmUrlProp, "LLM base URL:", "http://localhost:8080/");
        urlProp.setHelpText("<html>Must be an OpenAI-compatible server.<br>" +
                                    "Do not include the /v1/chat/completions part - just the base URL.</html>");
        urlProp.setAllowBlank(true); // blank means "feature disabled"
        urlProp.addFormFieldGenerationListener((prop, field) -> {
            field.addFieldValidator(new UrlValidator());
        });
        list.add(urlProp);
        list.add(new PasswordProperty(llmApiKeyProp, "LLM API key:")
                         .setAllowBlank(true) // blank mean no key required for this server (or it means 401 denied!)
                         .setPassword("")
                         .setHelpText("<html>Your API key for the LLM server.<br>" +
                                              "Not needed for some servers, like a local LLaMA instance.</html>"));
        list.add(new ShortTextProperty(llmModelProp, "LLM model name:", "gpt-3.5-turbo")
                         .setAllowBlank(true) // blank means not needed for this server
                         .setHelpText("<html>The name of the model to use for tag generation.</html>"));
        list.add(new ShortTextProperty(llmTagsProp, "LLM tag list:", "")
                         .setAllowBlank(true) // blank means the LLM will suggest tags without constraints
                         .setHelpText("<html>Comma-separated list of tags.<br>" +
                                              "If specified, tag generation will be restricted to just these.<br>" +
                                              "Leave blank to let the LLM decide (results unpredictable!)</html>"));
        list.add(new KeyStrokeProperty(autoTagKeyProp, "Auto-tag selected:",
                                       KeyStrokeManager.parseKeyStroke("F9"), // Why F9? I dunno.
                                       AutoTagAction.getInstance(requestTemplate))
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                         .setHelpText(
                                 "<html>Requests auto-tagging of the selected image from the configured LLM.</html>"));
        list.add(new KeyStrokeProperty(autoTagBatchKeyProp, "Auto-tag batch:",
                                       KeyStrokeManager.parseKeyStroke("Ctrl+F9"), // Why Ctrl+F9? I dunno.
                                       AutoTagBatchAction.getInstance(requestTemplate))
                         .setAllowBlank(true)
                         .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                         .setHelpText("<html>Shows a dialog that allows auto-tagging of all jpeg and/or png images" +
                                              "<br>in the current directory, with optional recursion.</html>"));
        LongTextProperty taggedProp = LongTextProperty.ofDynamicSizingMultiLine(sysPromptTaggedProp,
                                                                                "Tag-restricted prompt:");
        taggedProp.setHelpText(
                "<html>The system prompt to use for auto-tagging when the LLM tag list is specified.<br>" +
                        "<b>Modify with caution!</b></html>");
        taggedProp.setValue(sysPromptTagged);
        taggedProp.setAllowBlank(false);
        taggedProp.setAllowPopoutEditing(true);
        taggedProp.addFormFieldGenerationListener((prop, field) -> {
            ((LongTextField)field).getTextArea().setRows(6); // why is it so hard to set this?
        });
        list.add(taggedProp);
        LongTextProperty taglessProp = LongTextProperty.ofDynamicSizingMultiLine(sysPromptTaglessProp,
                                                                                 "Unrestricted prompt:");
        taglessProp.setHelpText(
                "<html>The system prompt to use for auto-tagging when the LLM tag list is <b>not</b> specified.<br>" +
                        "<b>Modify with caution!</b></html>");
        taglessProp.setValue(sysPromptUntagged);
        taglessProp.setAllowBlank(false);
        taglessProp.setAllowPopoutEditing(true);
        taglessProp.addFormFieldGenerationListener((prop, field) -> {
            ((LongTextField)field).getTextArea().setRows(6); // why is it so hard to set this?
        });
        list.add(taglessProp);

        // Add a few configurable hotkeys for commonly-used tags:
        for (int i = 1; i <= 8; i++) {
            // Why 8? I dunno, seems like a reasonable number.
            // Too many and the properties form will scroll vertically too much.
            // Too few and there's not enough value in this feature.
            // We'll use Ctrl+F1 through Ctrl+F8 as the default hotkeys, and there
            // will be no tags assigned by default. The user can configure
            // as needed, or blank them all out if they don't want to use this feature.
            String helpText = "<html>Assign commonly used tags to a hotkey for<br>"
                    + "quick application to the currently selected image.<br>" +
                    "You can comma-separate the list to set more than one tag at a time.<br>" +
                    "The tags are added to the image's tag list, unless they are already there.</html>";
            list.add(new TagHotkeyProperty(tagHotkeyPropPrefix + i, "Tag hotkey " + i + ":")
                             .setKeyStroke(KeyStrokeManager.parseKeyStroke("Ctrl+F" + i))
                             .setAllowBlank(true)
                             .setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES)
                             .setHelpText(helpText));
        }

        return list;
    }

    @Override
    public void onActivate() {
        TagIndex.getInstance().load();
        ReloadUIAction.getInstance().registerReloadable(this);
        sysPromptTagged = getLongTextPropValue(sysPromptTaggedProp, sysPromptTagged);
        sysPromptUntagged = getLongTextPropValue(sysPromptTaglessProp, sysPromptUntagged);
        AutoTagAction.getInstance(requestTemplate).setSysPrompts(sysPromptTagged, sysPromptUntagged);
        AutoTagBatchAction.getInstance(requestTemplate).setSysPrompts(sysPromptTagged, sysPromptUntagged);
    }

    @Override
    public void onDeactivate() {
        TagIndex.getInstance().save();
        ReloadUIAction.getInstance().unregisterReloadable(this);
        for (QuickTagPanel panel : quickTagPanels) {
            panel.dispose();
        }
        quickTagPanels.clear();
        tagPreviewPanels.clear();
    }

    /**
     * Returns the currently-configured position of our tag preview panel.
     * The available options are Top (above the main image), Bottom (below
     * the main image), or null, meaning that the tag preview panel is hidden.
     */
    private ExtraPanelPosition getTagPreviewPositionFromConfig() {
        PropertiesManager propsManager = AppConfig.getInstance().getPropertiesManager();
        //noinspection unchecked
        ComboProperty<String> prop = (ComboProperty<String>)propsManager.getProperty(tagPreviewPanelPositionProp);
        if (prop != null) {
            return switch (prop.getSelectedIndex()) {
                case 1 -> ExtraPanelPosition.Top;
                case 2 -> ExtraPanelPosition.Bottom;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Returns the currently-configured position of our quick tag panel.
     * We return this as a List, because we offer the option of allowing
     * multiple quick tag panels. So, the returned list may include
     * Left, Right, or both Left and Right. If the returned list is
     * empty, the quick tag panel is not to be shown at all.
     */
    private List<ExtraPanelPosition> getQuickTagPositionFromConfig() {
        PropertiesManager propsManager = AppConfig.getInstance().getPropertiesManager();
        //noinspection unchecked
        ComboProperty<String> prop = (ComboProperty<String>)propsManager.getProperty(quickTagPanelPositionProp);
        if (prop != null) {
            return switch (prop.getSelectedIndex()) {
                case 1 -> List.of(ExtraPanelPosition.Left);
                case 2 -> List.of(ExtraPanelPosition.Right);
                case 3 -> List.of(ExtraPanelPosition.Left, ExtraPanelPosition.Right);
                default -> List.of();
            };
        }
        return List.of();
    }

    /**
     * Overridden here so we can return our tag preview panel and/or quick tag panel,
     * if they are enabled in configuration.
     */
    @Override
    public JComponent getExtraPanelComponent(ExtraPanelPosition position) {
        if (position == getTagPreviewPositionFromConfig()) {
            TagPreviewPanel tagPreviewPanel = new TagPreviewPanel(); // create a new one for each request... other extensions may ask for it
            tagPreviewPanel.setName("ICE"); // short name in case our component gets added to a tab pane
            tagPreviewPanels.add(tagPreviewPanel);
            return tagPreviewPanel;
        }

        if (getQuickTagPositionFromConfig().contains(position)) {
            QuickTagPanel panel = new QuickTagPanel(position); // create a new one on each request
            quickTagPanels.add(panel);
            panel.setName("ICE"); // short name in case our component gets added to a tab pane
            return panel;
        }

        return null;
    }

    /**
     * We have a top-level "ICE" menu with various actions in it.
     * This menu is presented in all browse modes.
     */
    @Override
    public List<String> getTopLevelMenus(MainWindow.BrowseMode browseMode) {
        return List.of("ICE");
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        List<EnhancedAction> actions = new ArrayList<>();

        if (topLevelMenu.equals("ICE")) {
            actions.add(new SearchAction());
            actions.add(TagSingleImageAction.getInstance());
            actions.add(new TagMultipleImagesAction());

            // Only add scan actions if the tag index is enabled:
            if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM && TagIndex.isEnabled()) {
                actions.add(new ScanDirAction("Tag scan: current directory", false));
                actions.add(new ScanDirAction("Tag scan: current directory recursively", true));
            }

            actions.add(new TagStatsAction());

            if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM) {
                actions.add(new TagDirStatsAction());
                actions.add(new RandomImageSetAction());
            }

            // Auto-tag menu items:
            actions.add(AutoTagAction.getInstance(requestTemplate));
            if (browseMode == MainWindow.BrowseMode.FILE_SYSTEM) {
                // Batch mode only available if we're browsing directories:
                // (though it would be a neat feature to batch-tag all images in an image set... maybe later)
                actions.add(AutoTagBatchAction.getInstance(requestTemplate));
            }
        }

        return actions;
    }

    /**
     * This extension will write "ice" files alongside each image to store
     * tag information for the image. We'll mark these ice files as companion
     * files so that the parent application will handle copy/move/rename/delete
     * operations on them as the image itself is modified.
     */
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
        if (tagPreviewPanels.isEmpty()) {
            return;
        }

        for (TagPreviewPanel tagPreviewPanel : tagPreviewPanels) {
            tagPreviewPanel.clearTags();
        }

        File imageFile = selectedImage.getImageFile();
        if (imageFile != null && imageFile.exists()) {
            File file = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName()) + ".ice");
            if (! file.exists()) {
                return;
            }
            TagList tagList = TagList.fromFile(file);
            TagIndex.getInstance().addOrUpdateEntry(imageFile, file); // keep tag index up to date as we browse
            for (TagPreviewPanel tagPreviewPanel : tagPreviewPanels) {
                tagPreviewPanel.setTagList(tagList);
            }
        }
    }

    /**
     * Overridden here so we can keep our tag index up to date as images are moved, renamed,
     * deleted, copied, or symlinked.
     */
    @Override
    public void postImageOperation(ImageOperation.Type opType, File srcFile, File destFile) {
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

    /**
     * We can add a little hyperlink at the top of a thumbnail panel if the image has tags
     * associated with it. Clicking the hyperlink will bring up the tag editor for that image.
     * You can accomplish the same thing by hitting Ctrl+G or by clicking on the tag preview panel.
     */
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
                CompanionFileMouseListener listener = new CompanionFileMouseListener(srcFile, iceFile);
                iceLabel.addMouseListener(listener);
                thumbPanel.setExtraProperty("companionIceFileLabel", iceLabel);
                thumbPanel.setExtraProperty("companionIceFileLabelListener", listener);
                wrapperPanel.add(iceLabel);
            }

            thumbPanel.add(wrapperPanel, BorderLayout.NORTH);
        }
    }

    /**
     * Invoked when the image that this thumb panel represents has been renamed. We respond
     * to this by updating the hyperlink to point to the new companion file.
     * Note that we don't move the companion files here! File operations are handled
     * in preImageOperation() instead of here. This is the final step, invoked after the file
     * has been renamed, and we just need to update the stale hyperlinks to point to the
     * new files. This method does nothing if there is no companion file.
     *
     * @param thumbPanel The ThumbPanel in question.
     * @param newFile    A File object representing the new name.
     */
    @Override
    public void thumbPanelRenamed(ThumbPanel thumbPanel, File newFile) {
        File tagFile = new File(newFile.getParentFile(), FilenameUtils.getBaseName(newFile.getName()) + ".ice");
        CompanionFileMouseListener labelListener = (CompanionFileMouseListener)thumbPanel.getExtraProperty(
                "companionIceFileLabelListener");
        if (labelListener != null) {
            labelListener.setTagFile(tagFile);
        }
    }

    /**
     * Invoked when a ThumbPanel is selected or deselected. We respond to that by changing
     * colours as needed to indicate the selection state.
     *
     * @param thumbPanel The ThumbPanel in question
     * @param isSelected true if this thumb panel is selected.
     */
    @Override
    public void thumbPanelSelectionChanged(ThumbPanel thumbPanel, boolean isSelected) {
        JLabel textFileLabel = (JLabel)thumbPanel.getExtraProperty("companionIceFileLabel");
        if (textFileLabel != null) {
            if (isSelected) {
                textFileLabel.setForeground(LookAndFeelManager.getLafColor("textHighlightText", Color.BLUE));
            }
            else {
                textFileLabel.setForeground(LookAndFeelManager.getLafColor("Component.linkColor", Color.BLUE));
            }
        }
        JPanel wrapperPanel = (JPanel)thumbPanel.getExtraProperty("companionFileWrapperPanel");
        if (wrapperPanel != null) {
            wrapperPanel.setBackground(thumbPanel.getBackground());
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
        IntegerProperty fontSizeProp = (IntegerProperty)AppConfig.getInstance().getPropertiesManager().getProperty(
                IceExtension.fontSizeProp);
        if (fontSizeProp == null) {
            log.log(Level.SEVERE, "IceExtension: can't find our config property!");
            fontSizeProp = new IntegerProperty(IceExtension.fontSizeProp, "Hyperlink font size", 10, 8, 16, 1);
        }
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setFont(label.getFont().deriveFont((float)fontSizeProp.getValue()));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setForeground(LookAndFeelManager.getLafColor("Component.linkColor", Color.BLUE));
        return label;
    }

    @Override
    public void reloadUI() {
        for (QuickTagPanel panel : quickTagPanels) {
            panel.refreshPreferredWidth(); // user may have changed the preferred quick panel width
        }

        // Check if the user modified our LLM system prompt templates.
        String newTaggedPrompt = getLongTextPropValue(sysPromptTaggedProp, sysPromptTagged);
        String newTaglessPrompt = getLongTextPropValue(sysPromptTaglessProp, sysPromptUntagged);
        if (!newTaggedPrompt.equals(sysPromptTagged) || !newTaglessPrompt.equals(sysPromptUntagged)) {
            sysPromptTagged = newTaggedPrompt;
            sysPromptUntagged = newTaglessPrompt;
            AutoTagAction.getInstance(requestTemplate).setSysPrompts(sysPromptTagged, sysPromptUntagged);
            AutoTagBatchAction.getInstance(requestTemplate).setSysPrompts(sysPromptTagged, sysPromptUntagged);
        }
    }

    /**
     * Duplicated from swing-extras ResourceLoader class for use in this extension.
     * The problem is that ResourceLoader attempts to use its own class loader, which
     * won't work here in this extension, because we are loaded from a jar file
     * via our own class loader. This use case wasn't considered when ResourceLoader
     * was written, but it might be a nice feature request for swing-extras to allow
     * us to specify a class loader to use when loading resources, so we don't
     * have to do stuff like this.
     */
    private String getTextResource(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("Resource path cannot be null");
        }
        URL url = getClass().getClassLoader().getResource(resourcePath); // It has to be this.getClass()!
        if (url == null) {
            log.severe("Unable to load text resource from path: " + resourcePath);
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return String.join(System.lineSeparator(), lines);
        }
        catch (IOException ioe) {
            log.log(Level.SEVERE, "Caught IOException while loading text resource: " + ioe.getMessage(), ioe);
            return null;
        }
    }

    /**
     * Looks up the named LongTextProperty in AppConfig and returns its currently configured value.
     * Returns the given default value if the property is not found or is not a LongTextProperty.
     * Also returns null if the property value is null or blank.
     */
    private String getLongTextPropValue(String propName, String defaultValue) {
        AbstractProperty prop = AppConfig.getInstance().getPropertiesManager().getProperty(propName);
        if (prop instanceof LongTextProperty textProp) {
            String value = textProp.getValue();
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value;
        }
        else {
            // This can happen on a fresh install. Not a cause for alarm.
            return defaultValue;
        }
    }
}
