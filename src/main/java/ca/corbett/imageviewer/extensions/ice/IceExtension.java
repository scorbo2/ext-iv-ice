package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.LabelProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
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

    @Override
    public List<JMenu> getTopLevelMenus() {
        JMenu iceMenu = new JMenu("ICE");
        iceMenu.setMnemonic(KeyEvent.VK_I);
        iceMenu.add(new JMenuItem("TODO - ice menu items here"));
        return List.of(iceMenu);
    }
}
