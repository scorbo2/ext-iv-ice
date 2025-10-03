package ca.corbett.imageviewer.extensions.ice.threads;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressWorker;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndex;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * A worker thread to scan a given directory with optional recursion, adding or
 * updating entries to the TagIndex as needed. Does nothing if the tag index
 * is disabled in configuration.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ScanThread extends MultiProgressWorker {

    private static final Logger log = Logger.getLogger(ScanThread.class.getName());

    private final File startDir;
    private final boolean isRecursive;
    private boolean wasCanceled;

    private int entriesCreated;
    private int entriesUpdated;
    private int entriesSkippedBecauseUpToDate;

    public ScanThread(File startDir, boolean isRecursive) {
        this.startDir = startDir;
        this.isRecursive = isRecursive;
        wasCanceled = false;
    }

    public int getEntriesCreated() {
        return entriesCreated;
    }

    public int getEntriesUpdated() {
        return entriesUpdated;
    }

    public int getEntriesSkippedBecauseUpToDate() {
        return entriesSkippedBecauseUpToDate;
    }

    @Override
    public void run() {
        wasCanceled = false;
        if (! TagIndex.isEnabled()) {
            log.info("IceExtension: skipping tag scan because tag index is disabled. You can enable it in application settings.");
            return;
        }
        log.info("IceExtension: scanning "+startDir.getAbsolutePath() + (isRecursive?" recursively":""));
        entriesCreated = 0;
        entriesUpdated = 0;
        entriesSkippedBecauseUpToDate = 0;
        fireProgressBegins(2);
        List<File> tagFiles = FileSystemUtil.findFiles(startDir, isRecursive, "ice");
        fireMajorProgressUpdate(1, tagFiles.size(), "Scanning tag files...");
        int minorStep = 0;
        boolean shouldContinue;
        for (File tagFile : tagFiles) {
            shouldContinue = fireMinorProgressUpdate(1, minorStep++, tagFile.getName());
            if (! shouldContinue) {
                wasCanceled = true;
                break;
            }
            File imageFile = IceExtension.getMatchingImageFile(tagFile);
            if (imageFile == null) {
                continue;
            }

            switch (TagIndex.getInstance().addOrUpdateEntry(imageFile, tagFile)) {
                case ExistingEntryUpdated: entriesUpdated++; break;
                case NewEntryCreated: entriesCreated++; break;
                case SkippedBecauseUpToDate: entriesSkippedBecauseUpToDate++; break;
                case SkippedBecauseDisabled: break; // irrelevant as we check isEnabled() above
            }
        }

        log.info("IceExtension: tag scan complete. Entries added: " + entriesCreated
                         + "; entries updated: " + entriesUpdated
                         + "; entries skipped (unchanged): " + entriesSkippedBecauseUpToDate);

        // Auto-save if anything was changed:
        if (entriesCreated > 0 || entriesUpdated > 0) {
            TagIndex.getInstance().save();
        }

        if (wasCanceled) {
            fireProgressCanceled();
        }
        else {
            fireProgressComplete();
        }
    }
}
