package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.LabelProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IceExtension extends ImageViewerExtension {

    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/ice/extInfo.json";
    private final AppExtensionInfo extInfo;

    private static final String[] validPositions = {"Top", "Bottom"};

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
        list.add(new LabelProperty("ICE.General.warningLabel", "Note: restart is required to change tag panel position."));
        list.add(new ComboProperty<>("ICE.General.position", "Tag panel position:", Arrays.asList(validPositions), 1, false));
        return list;
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
        if (position == getConfiguredPanelPosition()) {
            return new TagPanel();
        }
        return null;
    }

    @Override
    public List<JMenu> getTopLevelMenus(MainWindow.BrowseMode browseMode) {
        JMenu iceMenu = new JMenu("ICE");
        iceMenu.setMnemonic(KeyEvent.VK_I);
        iceMenu.add(new JMenuItem("TODO - ice menu items here"));
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
                new TagDialog("Image tags", new TagList()).setVisible(true);
                return true;
            }
        }
        return false;
    }
}
