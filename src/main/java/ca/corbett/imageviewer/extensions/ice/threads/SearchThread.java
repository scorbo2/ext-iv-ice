package ca.corbett.imageviewer.extensions.ice.threads;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressWorker;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SearchThread extends MultiProgressWorker {

    private static final Logger log = Logger.getLogger(SearchThread.class.getName());

    public enum SearchMode {
        CONTAINS_ALL,
        CONTAINS_ANY,
        CONTAINS_NONE
    }

    private final File initialDir;
    private final TagList searchTags;
    private final boolean isRecursive;
    private final SearchMode searchMode;
    private final List<File> searchResults;
    private boolean wasCanceled;

    public SearchThread(File initialDir, TagList tagList, boolean isRecursive, SearchMode searchMode) {
        this.initialDir = initialDir;
        this.searchTags = tagList;
        this.isRecursive = isRecursive;
        this.searchMode = searchMode;
        searchResults = new ArrayList<>();
        wasCanceled = false;
    }

    public List<File> getSearchResults() {
        return new ArrayList<>(searchResults);
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    @Override
    public void run() {
        fireProgressBegins(1);
        List<File> iceFiles = FileSystemUtil.findFiles(initialDir, isRecursive, "ice");
        searchResults.clear();
        wasCanceled = false;
        int currentStep = 0;
        for (File candidateFile : iceFiles) {
            log.fine("ICE SearchThread: Considering "+candidateFile.getAbsolutePath());
            fireMajorProgressUpdate(1, iceFiles.size(), "Searching...");
            boolean shouldContinue = fireMinorProgressUpdate(1, currentStep, candidateFile.getName());
            File imageFile = IceExtension.getMatchingImageFile(candidateFile);
            if (imageFile != null) {
                log.fine("ICE SearchThread: Found matching image file "+imageFile.getAbsolutePath());
                TagList tagList = TagList.fromFile(candidateFile);
                boolean isMatch = switch (searchMode) {
                    case CONTAINS_ALL -> tagList.containsAll(searchTags);
                    case CONTAINS_ANY -> tagList.containsAny(searchTags);
                    case CONTAINS_NONE -> tagList.containsNone(searchTags);
                };
                if (isMatch) {
                    log.fine("ICE SearchThread: search matched!");
                    searchResults.add(imageFile);
                }
                else {
                    log.fine("ICE SearchThread: search did not match.");
                }
            }
            else {
                log.fine("ICE SearchThread: no matching image file.");
            }
            currentStep++;
            if (! shouldContinue) {
                wasCanceled = true;
                break;
            }
        }
        if (wasCanceled) {
            log.fine("ICE SearchThread: search was canceled by user input.");
            fireProgressCanceled();
        }
        else {
            log.fine("ICE SearchThread: search complete with "+searchResults.size()+" results.");
            fireProgressComplete();
        }
    }
}
