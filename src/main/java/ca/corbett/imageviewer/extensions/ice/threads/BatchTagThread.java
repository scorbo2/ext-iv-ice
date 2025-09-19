package ca.corbett.imageviewer.extensions.ice.threads;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ThumbContainerPanel;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.List;

/**
 * TODO update search index if available
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchTagThread extends SimpleProgressWorker {

    private final File startDir;
    private final boolean isRecursive;
    private final boolean isReplaceTags;
    private final TagList tagList;
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
        List<File> imageFiles = FileSystemUtil.findFiles(startDir, isRecursive, ThumbContainerPanel.getImageExtensions());
        fireProgressBegins(imageFiles.size());
        int currentStep = 1;
        for (File candidate : imageFiles) {
            TagList tagsToModify = TagList.fromFile(new File(candidate.getParentFile(), FilenameUtils.getBaseName(candidate.getName())+".ice"));
            if (isReplaceTags) {
                tagsToModify.clear();
            }
            tagsToModify.addAll(tagList);
            tagsToModify.save();
            if (! fireProgressUpdate(currentStep, candidate.getName())) {
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
}
