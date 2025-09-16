package ca.corbett.imageviewer.extensions.ice;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import java.util.List;

public class IceExtension extends ImageViewerExtension {

    private static final String extInfoLocation = "/ca/corbett/imageviewer/extensions/ice/extInfo.json";
    private final AppExtensionInfo extInfo;

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
        return List.of();
    }
}
