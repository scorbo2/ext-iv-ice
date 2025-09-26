package ca.corbett.imageviewer.extensions.ice.threads;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ThumbContainerPanel;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Goes through a directory with optional recursion and applies the given tags to every image that it finds there.
 * You can specify whether the new tags should replace any existing tags, or be added to them (with pruning
 * of duplicate tags).
 * <p>
 *     <b>Token replacement</b> - there are some tokens you can specify in the TagImagesDialog that
 *     we will dynamically replace here with information about the image:
 * </p>
 * <ul>
 *     <li><b>$(imageDirName)</b> - will be replaced with the name of the directory containing the image.
 *     <li><b>$(imageDirName)</b> - full path and name of image directory.
 *     <li><b>$(parentDirName)</b> - will be replaced with the name of the parent directory.
 *     <li><b>$(parentDirPath)</b> - full path and name of parent directory.
 *     <li><b>$(aspectRatio)</b> - will be replaced with a fixed value of "square", "portrait", or "landscape"
 *         depending on image dimensions. Note that specifying this token makes the batch operation take MUCH
 *         longer, as we have to load each image to get its dimensions. Omitting this token will greatly
 *         speed up the process.
 * </ul>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchTagThread extends SimpleProgressWorker {

    private static final Logger log = Logger.getLogger(BatchTagThread.class.getName());

    public static final String IMAGE_DIR_NAME_TOKEN = "$(imageDirName)";
    public static final String IMAGE_DIR_PATH_TOKEN = "$(imageDirPath)";
    public static final String PARENT_DIR_NAME_TOKEN = "$(parentDirName)";
    public static final String PARENT_DIR_PATH_TOKEN = "$(parentDirPath)";
    public static final String ASPECT_RATIO_TOKEN = "$(aspectRatio)";

    private final File startDir;
    private final boolean isRecursive;
    private final boolean isReplaceTags;
    private final TagList tagList;
    private int totalProcessed;
    private int countCreated;
    private int countUpdated;
    private boolean wasCanceled;

    public BatchTagThread(File dir, boolean isRecursive, boolean isReplaceTags, TagList tagList) {
        this.startDir = dir;
        this.isRecursive = isRecursive;
        this.isReplaceTags = isReplaceTags;
        this.tagList = tagList;
        this.wasCanceled = false;
    }

    @Override
    public void run() {
        wasCanceled = false;
        totalProcessed = 0;
        countCreated = 0;
        countUpdated = 0;
        List<File> imageFiles = FileSystemUtil.findFiles(startDir, isRecursive, ThumbContainerPanel.getImageExtensions());
        fireProgressBegins(imageFiles.size());
        int currentStep = 1;
        for (File imageFile : imageFiles) {
            File tagFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName())+".ice");
            if (tagFile.exists()) {
                countUpdated++;
            }
            else {
                countCreated++;
            }
            TagList tagsToModify = TagList.fromFile(tagFile);
            if (isReplaceTags) {
                tagsToModify.clear();
            }
            tagsToModify.addAll(tagList);
            handleTokenReplacements(tagsToModify, imageFile);
            tagsToModify.save();
            TagIndex.getInstance().addOrUpdateEntry(imageFile, tagFile);
            totalProcessed++;
            if (! fireProgressUpdate(currentStep, imageFile.getName())) {
                wasCanceled = true;
                break;
            }
        }

        if (wasCanceled) {
            fireProgressCanceled();
        }
        else {
            fireProgressComplete();
        }
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public int getCountCreated() {
        return countCreated;
    }

    public int getCountUpdated() {
        return countUpdated;
    }

    protected void handleTokenReplacements(TagList tagList, File imageFile) {
        if (tagList.hasTag(IMAGE_DIR_NAME_TOKEN)) {
            tagList.replace(IMAGE_DIR_NAME_TOKEN, imageFile.getParentFile().getName());
        }
        if (tagList.hasTag(IMAGE_DIR_PATH_TOKEN)) {
            tagList.replace(IMAGE_DIR_PATH_TOKEN, imageFile.getParent());
        }
        if (tagList.hasTag(PARENT_DIR_NAME_TOKEN)) {
            tagList.replace(PARENT_DIR_NAME_TOKEN, imageFile.getParentFile().getParentFile().getName());
        }
        if (tagList.hasTag(PARENT_DIR_PATH_TOKEN)) {
            tagList.replace(PARENT_DIR_PATH_TOKEN, imageFile.getParentFile().getParent());
        }
        if (tagList.hasTag(ASPECT_RATIO_TOKEN)) {
            try {
                tagList.replace(ASPECT_RATIO_TOKEN, getAspectRatioDescription(getImageDimensions(imageFile)));
            }
            catch (IOException ioe) {
                log.log(Level.SEVERE, "BatchTagThread: Caught exception while tagging images: "+ioe.getMessage(), ioe);
                tagList.remove(ASPECT_RATIO_TOKEN); // just remove it
            }
        }
    }

    /**
     * Reads image dimensions without loading the full image into memory
     * Parking this here until <a href="https://github.com/scorbo2/swing-extras/issues/129">swing-extras 129</a>
     * is addressed - this should be in ImageUtil.
     */
    public static Dimension getImageDimensions(File imageFile) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(imageFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);

            if (!readers.hasNext()) {
                throw new IOException("No ImageReader found for the image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);  // 0 = first image
                int height = reader.getHeight(0);
                return new Dimension(width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private String getAspectRatioDescription(Dimension imageDim) {
        final double TOLERANCE = 0.05; // five percent is "close enough"
        int width = imageDim.width;
        int height = imageDim.height;

        // Calculate the ratio of the smaller dimension to the larger dimension
        double ratio = (double) Math.min(width, height) / Math.max(width, height);

        // If the ratio is close enough to 1.0 (square), consider it square
        if (ratio >= (1.0 - TOLERANCE)) {
            return "square";
        }

        // Otherwise, determine landscape vs portrait
        if (width > height) {
            return "landscape";
        } else {
            return "portrait";
        }
    }
}
