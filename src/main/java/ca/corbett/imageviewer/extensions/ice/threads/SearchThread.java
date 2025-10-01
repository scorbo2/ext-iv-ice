package ca.corbett.imageviewer.extensions.ice.threads;

import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.MultiProgressWorker;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.imagesets.ImageSet;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Searches through all tag files in a given directory with optional recursion, looking for any
 * that match the specified tag list with the specified search mode. If the tag index is enabled
 * in settings, it will be consulted to potentially greatly speed up the search.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class SearchThread extends MultiProgressWorker {

    private static final Logger log = Logger.getLogger(SearchThread.class.getName());

    private final File initialDir;
    private final boolean isRecursive;
    private final List<ImageSet> imageSetsToSearch;
    private final TagList searchTagsAll;
    private final TagList searchTagsAny;
    private final TagList searchTagsNone;
    private final List<File> searchResults;
    private boolean wasCanceled;

    public SearchThread(File initialDir, boolean isRecursive, TagList findAll, TagList findAny, TagList findNone) {
        this.initialDir = initialDir;
        this.isRecursive = isRecursive;
        this.imageSetsToSearch = null;
        this.searchTagsAll = findAll;
        this.searchTagsAny = findAny;
        this.searchTagsNone = findNone;
        searchResults = new ArrayList<>();
        wasCanceled = false;
    }

    public SearchThread(List<ImageSet> imageSets, TagList findAll, TagList findAny, TagList findNone) {
        this.initialDir = null;
        this.isRecursive = false;
        this.imageSetsToSearch = new ArrayList<>(imageSets);
        this.searchTagsAll = findAll;
        this.searchTagsAny = findAny;
        this.searchTagsNone = findNone;
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
        // Log the ridiculous case where caller provided no search tags at all:
        if (searchTagsAll.isEmpty() && searchTagsAny.isEmpty() && searchTagsNone.isEmpty()) {
            log.warning("ICE SearchThread executed with no search tags! All images will match.");
        }

        fireProgressBegins(2);
        List<File> iceFiles = getTagFiles();
        searchResults.clear();
        wasCanceled = false;
        int currentStep = 0;
        int indexHits = 0;
        int indexMisses = 0;
        for (File candidateFile : iceFiles) {
            log.fine("ICE SearchThread: Considering "+candidateFile.getAbsolutePath());
            fireMajorProgressUpdate(1, iceFiles.size(), "Searching...");
            boolean shouldContinue = fireMinorProgressUpdate(1, currentStep, candidateFile.getName());
            File imageFile = IceExtension.getMatchingImageFile(candidateFile);
            if (imageFile != null) {
                log.fine("ICE SearchThread: Found matching image file "+imageFile.getAbsolutePath());

                boolean isMatch;

                // Give the tag index first crack at it:
                if (TagIndex.isEnabled() && TagIndex.getInstance().IsIndexedAndUpToDate(imageFile, candidateFile)) {
                    isMatch = true;
                    if (! searchTagsAll.isEmpty()) {
                        isMatch = TagIndex.getInstance().containsAll(imageFile, searchTagsAll);
                    }
                    if (isMatch && ! searchTagsAny.isEmpty()) {
                        isMatch = TagIndex.getInstance().containsAny(imageFile, searchTagsAny);
                    }
                    if (isMatch && ! searchTagsNone.isEmpty()) {
                        isMatch = TagIndex.getInstance().containsNone(imageFile, searchTagsNone);
                    }
                    indexHits++;
                }

                // If not found in the index or if index is disabled, build a new one:
                else {
                    TagList tagList = TagList.fromFile(candidateFile);
                    if (TagIndex.isEnabled()) {
                        TagIndex.getInstance().addOrUpdateEntry(imageFile, candidateFile);
                    }

                    isMatch = true;
                    if (! searchTagsAll.isEmpty()) {
                        isMatch = tagList.containsAll(searchTagsAll);
                    }
                    if (isMatch && ! searchTagsAny.isEmpty()) {
                        isMatch = tagList.containsAny(searchTagsAny);
                    }
                    if (isMatch && ! searchTagsNone.isEmpty()) {
                        isMatch = tagList.containsNone(searchTagsNone);
                    }
                    indexMisses++;
                }

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
            if (TagIndex.isEnabled()) {
                log.info("IceExtension: search complete with " + searchResults.size() + " results ("
                                 + iceFiles.size() + " tag files found, "
                                 + indexHits + " indexed, "
                                 + indexMisses + " not indexed).");
            }
            else {
                log.info("IceExtension: search complete with "+searchResults.size() + " results ("
                                 + "tag index is disabled! Enable it in application settings to speed up searches)");
            }
            fireProgressComplete();
        }
    }

    private List<File> getTagFiles() {
        if (initialDir != null) {
            return FileSystemUtil.findFiles(initialDir, isRecursive, "ice");
        }

        List<File> tagFiles = new ArrayList<>();
        for (ImageSet imageSet : imageSetsToSearch) {
            for (String filePath : imageSet.getImageFilePaths()) {
                File imageFile = new File(filePath);
                File tagFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName())+".ice");
                if (tagFile.exists()) {
                    tagFiles.add(tagFile);
                }
            }
        }
        return tagFiles;
    }
}
