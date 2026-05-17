package ca.corbett.imageviewer.extensions.ice.llm;

import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.PasswordProperty;
import ca.corbett.extras.properties.PropertiesManager;
import ca.corbett.extras.properties.ShortTextProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.MainWindow;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Responsible for sending image analysis requests to a configured LLM for auto-tagging.
 * This is an experimental feature, and all of this is subject to change.
 * <p>
 * Some TODO items, in no particular order:
 * </p>
 * <ul>
 *     <li>Right now we're using template json from our jar resources and just search and replacing certain keys
 *         to form the request. This works, but is overly simplistic. We should probably use the openai-java
 *         library instead of forming raw REST requests ourselves.</li>
 *     <li>Currently we lean heavily on logging to report errors. We should have an ErrorCallback
 *         so that we can properly display an error dialog to the user if something goes wrong.
 *         Basically, if the user isn't watching the log output, they may have no idea what happened.</li>
 *     <li>Currently, you can only request an auto-tag for the selected image. It would be great to
 *         have a "batch mode", where it goes through all images in a directory, with optional recursion.</li>
 *     <li>Currently, the auto-tag can only be triggered from a configurable keyboard shortcut. It would be
 *         nice to have a menu item or toolbar button or right-click popup option or something.</li>
 *     <li>Currently, we blindly append all LLM-suggested tags to the image's tag list. Would be a much
 *         nicer UX to pop up a dialog showing the suggested tags, and allow user editing before
 *         confirmation. This is especially nice if the LLM was not given a constrained tag list, because
 *         the suggested tags may be wrong (or inconsistent).</li>
 * </ul>
 * <p>
 *     The above TODOs will be handled in future tickets, if the initial proof-of-concept works out.
 *     The current implementation is minimal.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class AiConnectionManager {

    @FunctionalInterface
    public interface CompletionCallback {
        void onComplete(TagList tagList);
    }

    @FunctionalInterface
    public interface ErrorCallback {
        void onError(AiErrorBody error);
    }

    private static final Logger log = Logger.getLogger(AiConnectionManager.class.getName());

    private final String jsonTemplate;
    private final String jsonTemplateTagless;
    private boolean featureEnabled;
    private URL llmUrl;
    private String llmModel;
    private String llmApiKey;
    private TagList llmTags;

    /**
     * If the LLM is unable to determine tags for some reason (the image is unclear, the given tag
     * list was too restrictive, or the LLM feature itself is disabled), then we will return this special tag.
     */
    public static final String NO_TAGS = "none";

    // Our search and replace keys for preparing our request json from templates:
    public static final String KEY_MODEL = "{{MODEL}}";
    public static final String KEY_IMG_DATA = "{{IMGDATA}}";
    public static final String KEY_TAGS = "{{TAGS}}";

    /**
     * The two json template strings must be supplied here (they do not change dynamically).
     * <ul>
     *     <li><b>jsonTemplate</b> - If the user gives us a specific list of tags, we will constrain the LLM
     *         to using ONLY those tags in its response. This is the preferred use case.</li>
     *     <li><b>jsonTemplateTagless</b> - For a more YOLO experience, the user can leave the tag list blank,
     *         and we'll leave it entirely up to the LLM to decide on tags. Results not guaranteed to make sense!</li>
     * </ul>
     * <p>
     *     If either template is blank or null, the LLM connection feature is disabled, and every
     *     request will return the "none" response.
     * </p>
     *
     * @param jsonTemplate        The json template for a tag-constrained analysis request. If blank, the feature is disabled.
     * @param jsonTemplateTagless The json template for a tagless analysis request. If blank, the feature is disabled.
     */
    public AiConnectionManager(String jsonTemplate, String jsonTemplateTagless) {
        this.jsonTemplate = jsonTemplate;
        this.jsonTemplateTagless = jsonTemplateTagless;

        // We'll load this here in the constructor.
        // Our assumption is that this class is instantiated on demand, rather than keeping an instance around.
        // So, we don't need to wire up to the UIReload mechanism to watch for config changes.
        populateConfig();
    }

    /**
     * Starts an async request to auto-tag the given image with our configured LLM.
     * Your onComplete callback will receive the resulting TagList when the request is complete.
     * If the image could not be auto-tagged for any reason, the TagList will have a single
     * entry with the special NO_TAGS tag. This also applies if the LLM feature is disabled.
     * The callback may be invoked from the worker thread! Take care to check if you are on
     * the UI thread when your callback is invoked, and if not, use SwingUtilities.invokeLater()
     * to switch to the UI thread before making any UI updates.
     *
     * @param imageFile  The image file to auto-tag. Must not be null. Currently only PNG and JPEG formats are supported.
     * @param onComplete The CompletionCallback to invoke when done. Must not be null.
     * @param onError   The ErrorCallback to invoke if something goes wrong. Must not be null.
     */
    public void requestAutoTag(File imageFile, CompletionCallback onComplete, ErrorCallback onError) {
        if (imageFile == null) {
            throw new IllegalArgumentException("Image file must not be null");
        }
        if (!imageFile.exists() || !imageFile.isFile() || !imageFile.canRead()) {
            throw new IllegalArgumentException("Image file must exist and be readable: " + imageFile.getAbsolutePath());
        }
        String filename = imageFile.getName().toLowerCase();
        if (!(filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg"))) {
            throw new IllegalArgumentException("Unsupported image format for file: " + imageFile.getAbsolutePath()
                                                       + " - only PNG and JPEG are supported");
        }
        if (onComplete == null || onError == null) {
            throw new IllegalArgumentException("Completion and error callbacks must not be null");
        }
        if (!featureEnabled) {
            log.warning("LLM auto-tagging requested but feature is disabled - returning NO_TAGS");
            TagList list = new TagList();
            list.add(NO_TAGS);
            onComplete.onComplete(list);
            return;
        }

        // Fire off a worker thread to do this for us:
        MultiProgressDialog dialog = new MultiProgressDialog(MainWindow.getInstance(), "Auto-tagging image...");
        AiRequestThread requestThread = new AiRequestThread(imageFile, this, onComplete, onError);
        dialog.runWorker(requestThread, true);
    }

    public String getJsonTemplate() {
        return jsonTemplate;
    }

    public String getJsonTemplateTagless() {
        return jsonTemplateTagless;
    }

    public URL getLlmUrl() {
        return llmUrl;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public TagList getLlmTags() {
        return llmTags;
    }

    /**
     * Grabs our config from AppConfig and validates it.
     * This will set featureEnabled to false if our config is invalid.
     */
    private void populateConfig() {
        // Start from a fully-disabled state:
        featureEnabled = false;
        llmUrl = null;
        llmModel = null;
        llmApiKey = null;
        llmTags = null;

        // If either of our templates are null, there's no point in continuing:
        if (jsonTemplate == null || jsonTemplateTagless == null) {
            log.warning("LLM JSON templates not supplied - LLM feature is disabled :(");
            return;
        }

        // Now take a shot at populating everything:
        PropertiesManager propsManager = AppConfig.getInstance().getPropertiesManager();
        AbstractProperty prop = propsManager.getProperty(IceExtension.llmUrlProp);
        if (prop instanceof ShortTextProperty urlProp) {
            try {
                String urlString = urlProp.getValue();
                if (urlString == null || urlString.isBlank()) {
                    log.warning("LLM URL is blank - LLM feature is disabled :(");
                    return;
                }
                llmUrl = new URL(urlString.trim()); // this gives us the base url, example http://localhost:8080
                llmUrl = appendPath(llmUrl, "/v1/chat/completions"); // we'll hard-code the API path
            }
            catch (Exception e) {
                log.severe("Invalid LLM URL: " + urlProp.getValue() + " - disabling LLM feature");
                return;
            }
        }
        else {
            log.severe("LLM URL property is not a ShortTextProperty - disabling LLM feature");
            return;
        }
        prop = propsManager.getProperty(IceExtension.llmModelProp);
        if (prop instanceof ShortTextProperty modelProp) {
            llmModel = modelProp.getValue().trim();
            if (llmModel.isEmpty()) {
                // This is not necessarily a problem, but we have no way of knowing if the server requires it or not:
                // We could log a warning here, but it feels too noisy. So we'll put it at FINE level.
                log.fine("LLM model is blank - if your server requires a model name, requests will fail.");
            }
        }
        else {
            log.severe("LLM model property is not a ShortTextProperty - disabling LLM feature");
            return;
        }
        prop = propsManager.getProperty(IceExtension.llmApiKeyProp);
        if (prop instanceof PasswordProperty apiKeyProp) {
            llmApiKey = apiKeyProp.getPassword().trim();
            if (llmApiKey.isEmpty()) {
                // This is not necessarily a problem, but we have no way of knowing if the server requires it or not:
                // Again, we could log this, but it feels noisy. If the user gets a 401, they'll know why.
                log.fine("LLM API key is blank - if authentication is required, requests will fail.");
            }
        }
        else {
            log.severe("LLM API key property is not a PasswordProperty - disabling LLM feature");
            return;
        }
        prop = propsManager.getProperty(IceExtension.llmTagsProp);
        if (prop instanceof ShortTextProperty tagsProp) {
            llmTags = TagList.of(tagsProp.getValue()); // TagList can parse this for us
            if (llmTags.isEmpty()) {
                // This may result in very unexpected behavior. Perhaps the user is unaware of the consequences here:
                log.warning("LLM tags list is empty - the LLM will be free to choose any tags it wants!" +
                                    " This may result in unpredictable or inconsistent tags being chosen. " +
                                    "You can supply a tag list in configuration to restrict the LLM.");
            }
        }
        else {
            log.severe("LLM tags property is not a ShortTextProperty - disabling LLM feature");
            return;
        }

        // All good!
        featureEnabled = true;
    }

    /**
     * Our users are explicitly request to provide just the base url without the API path.
     * We will hard-code the path on top of this base url.
     *
     * @param baseUrl Something like <code>http://localhost:8080</code>
     * @param path    Something like /v1/chat/completions
     * @return Hopefully something like <code>http://localhost:8080/v1/chat/completions</code>
     * @throws MalformedURLException If something goes wrong
     */
    private static URL appendPath(URL baseUrl, String path) throws MalformedURLException {

        // 1. Convert the URL object to a clean string representation
        String originalUrlString = baseUrl.toExternalForm();

        // 2. Ensure base URL ends with a slash:
        if (!originalUrlString.endsWith("/")) {
            originalUrlString += "/";
        }

        // If the given path starts with a slash, remove it to avoid double slashes:
        String newPath = path.startsWith("/") ? path.substring(1) : path;

        // Now we can safely concatenate them:
        String finalUrlString = originalUrlString + newPath;

        // 3. Create a new URL instance
        return new URL(finalUrlString);
    }
}
