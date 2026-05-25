package ca.corbett.imageviewer.extensions.ice.llm;

import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.extensions.ice.TagList;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A background thread to fire off an http request to the configured LLM
 * and retrieve the tag list for a given image.
 * <p>
 * <b>Implementation note:</b> we are NOT using the openai-java library for this,
 * because we have a very simple use case of a single request and response.
 * So, we'll just use Java's HttpClient and form the request body ourselves
 * from our json templates. This is subject to change if it turns out that this
 * feature actually works! This is all experimental code for now.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class AiRequestThread extends SimpleProgressWorker {

    private static final Logger log = Logger.getLogger(AiRequestThread.class.getName());

    private final File imageFile;
    private final AiConnectionManager manager;
    private final AiConnectionManager.CompletionCallback onComplete;
    private final AiConnectionManager.ErrorCallback onError;
    private boolean chatty;
    private final long connectTimeoutMS;
    private final long requestTimeoutMS;
    private final boolean includeExistingTags;

    public AiRequestThread(File imageFile,
                           AiConnectionManager manager,
                           AiConnectionManager.CompletionCallback onComplete,
                           AiConnectionManager.ErrorCallback onError) {
        this.manager = manager;
        this.onComplete = onComplete;
        this.onError = onError;
        this.imageFile = imageFile;
        chatty = true;

        // Look up our config settings now, before the thread starts:
        connectTimeoutMS = IceExtension.getConnectTimeoutMS();
        requestTimeoutMS = IceExtension.getRequestTimeoutMS();
        includeExistingTags = IceExtension.getIncludeExistingTagsOption();
    }

    /**
     * Reports whether this thread will emit log messages during request execution.
     */
    public boolean isChatty() {
        return chatty;
    }

    /**
     * By default, this thread will log success/failure messages regarding the request.
     * If you plan to do your own logging, you can tell this thread to be quiet by setting chatty to false.
     */
    public void setChatty(boolean chatty) {
        this.chatty = chatty;
    }

    @Override
    public void run() {
        // Let's get the progress bar up:
        // There are 3 steps:
        //    1) downscale if necessary
        //    2) base64 encode the image
        //    3) make the request
        fireProgressBegins(3);
        try {

            // These were validated by our AiManager, so we won't do it here again:
            URL url = manager.getLlmUrl();
            String model = manager.getLlmModel();
            String apiKey = manager.getLlmApiKey();
            TagList llmTags = manager.getLlmTags();

            // Load the existing tags for this image, if any:
            // (but only if that option is enabled, to save ourselves some I/O on every image file if not):
            File tagFile = new File(imageFile.getParentFile(), FilenameUtils.getBaseName(imageFile.getName()) + ".ice");
            TagList existingTags = includeExistingTags ? TagList.fromFile(tagFile) : new TagList();

            // We'll start with the mime type of the original image:
            String base64ImageData;
            String mimeType = imageFile.getName().toLowerCase(Locale.ROOT)
                                       .endsWith(".png") ? "image/png" : "image/jpeg";

            try {
                // Downscale the image if necessary (downscaleIfNecessary returns null if no downscale is needed):
                fireProgressUpdate(0, "Downscale check...");
                byte[] imageBytes = downscaleIfNecessary(imageFile);
                if (imageBytes == null) {
                    // No downscale needed! Just read the file as-is:
                    imageBytes = Files.readAllBytes(imageFile.toPath());
                }
                else {
                    // We downscaled it! Log this.
                    // Executive decision: ignore the "chatty" setting, as this is important:
                    String originalSize = FileSystemUtil.getPrintableSize(imageFile.length());
                    String newSize = FileSystemUtil.getPrintableSize(imageBytes.length);
                    log.info("Scaled oversized image from " + originalSize + " to " + newSize
                                     + " - (original image file not affected). "
                                     + "You can change the downscale threshold in application settings.");
                    mimeType = "image/jpeg"; // Update the mime type since we converted to jpeg
                }

                // Base64-encode the image file:
                fireProgressUpdate(1, "Encoding image...");
                base64ImageData = Base64.getEncoder().encodeToString(imageBytes);
            }
            catch (Exception e) {
                log.severe("Failed to read and encode image file: " + e.getMessage());
                onError.onError(AiErrorBody.of(e.getClass().getName(),
                                               "Failed to read and encode image file: " + e.getMessage()));
                return;
            }

            // Log a nag warning if we have no LLM tag restrictions, since this may lead to unexpected results:
            // (this can be disabled in the config if the user knows what they're doing)
            if (llmTags.isEmpty() && manager.isWarnOnUnrestrictedTagList()) {
                // This may result in very unexpected behavior. Perhaps the user is unaware of the consequences here:
                log.warning("Auto-tag: the LLM tags list is empty - the LLM will be free to choose any tags it wants!" +
                                    " This may result in unpredictable or inconsistent tags being chosen. " +
                                    "You can supply a tag list in configuration to restrict the LLM.");
            }

            // Do our tag substitution in our template to get the actual request body:
            String jsonBody = prepareRequestBody(model, llmTags, existingTags, base64ImageData, mimeType);

            // Now we can fire off the request and parse the response:
            try {
                fireProgressUpdate(2, "Sending request to LLM...");
                HttpClient client = HttpClient.newBuilder()
                                              .connectTimeout(Duration.ofMillis(connectTimeoutMS))
                                              .build();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                                .uri(url.toURI())
                                                                .header("Content-Type", "application/json")
                                                                .timeout(Duration.ofMillis(requestTimeoutMS));
                if (!apiKey.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }

                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                ObjectMapper objectMapper = new ObjectMapper();

                if (response.statusCode() != 200) {
                    try {
                        // We can try to parse out what happened:
                        AiErrorBody errorBody = objectMapper.readValue(response.body(), AiErrorBody.class);
                        if (chatty) {
                            log.severe("LLM request failed with status code " + response.statusCode() + ": "
                                               + errorBody.getMessage() + " (code " + errorBody.getCode() + ", type "
                                               + errorBody.getType() + ")");
                        }
                        onError.onError(errorBody);
                    }
                    catch (Exception e) {
                        // We were unable to parse it, so best we can do is show the generic error code:
                        // (we *could* log the response body, but it might be huge, and it might leak
                        //  server info into the log, so we'll just let it go)
                        if (chatty) {
                            log.severe("LLM request failed with status code " + response.statusCode());
                        }
                        onError.onError(AiErrorBody.of(response.statusCode(),
                                                       "HTTP error",
                                                       "LLM request failed with status code " + response.statusCode()));
                    }
                    return;
                }

                // We'll use Jackson to parse the json response:
                AiCompletionsBody responseObject = objectMapper.readValue(response.body(), AiCompletionsBody.class);
                if (chatty) {
                    log.info("Auto-tag: received response from LLM: finish_reason=" + responseObject.getFinishReason()
                                     + ", output=" + responseObject.getOutput());
                }
                TagList results = responseObject.getOutput();
                if (results.isEmpty()) {
                    if (chatty) {
                        log.warning("LLM response did not contain any tags. Returning NO_TAGS.");
                    }
                    onComplete.onComplete(noTags());
                }
                else {
                    onComplete.onComplete(results);
                }
            }
            catch (Exception e) {
                if (chatty) {
                    log.log(Level.SEVERE, "Failed to send LLM request or parse response: " + e.getMessage(), e);
                }
                onError.onError(AiErrorBody.of(e.getClass().getName(),
                                               "Failed to send LLM request or parse response: " + e.getMessage()));
            }
        }
        finally {
            // We MUST fire progressComplete, no matter what happened above, or the progress dialog will never close:
            fireProgressComplete();
        }
    }

    private TagList noTags() {
        TagList badList = new TagList();
        badList.add(AiConnectionManager.NO_TAGS);
        return badList;
    }

    private String prepareRequestBody(String model, TagList tags, TagList existingTags, String base64ImageData, String mimeType) {
        // Figure out which system prompt we need:
        String sysPrompt = tags.isEmpty() ? manager.getTaglessPrompt() : manager.getTaggedPrompt();

        // Handle safe escaping of our String inputs:
        // (things like embedded quotation marks or line breaks can cause problems for us.)
        // (note that we don't worry about the base64 image data, since it should already be json-safe.)
        String safeSysPrompt = new String(new SerializedString(sysPrompt).asQuotedChars());
        String safeUserPrompt = new String(new SerializedString(getUserPrompt(existingTags)).asQuotedChars());
        String safeModel = new String(new SerializedString(model).asQuotedChars());
        String safeTags = new String(new SerializedString(tags.toString()).asQuotedChars());

        // Now we can safely do our string replacement to get the final request body:
        return manager.getRequestTemplate()
                      .replace(AiConnectionManager.KEY_SYS_PROMPT, safeSysPrompt)
                      .replace(AiConnectionManager.KEY_USER_PROMPT, safeUserPrompt)
                      .replace(AiConnectionManager.KEY_MODEL, safeModel)
                      .replace(AiConnectionManager.KEY_IMG_DATA, base64ImageData)
                      .replace(AiConnectionManager.KEY_TAGS, safeTags)
                      .replace(AiConnectionManager.KEY_MIME_TYPE, mimeType);
    }

    private String getUserPrompt(TagList existingTags) {
        String userPrompt = "Please tag this image."; // simple prompt that defers entirely to the SYS_PROMPT.

        if (includeExistingTags && !existingTags.isEmpty()) {
            // Fancy prompt that tries to provide additional guidance based on what's already there:
            userPrompt += " The existing tags for this image are: "
                    + existingTags.toString() + ". "
                    + "Only return tags if they are not already covered by the existing tags. "
                    + "If the image is already adequately tagged, return the fixed string 'none'.";
        }

        return userPrompt;
    }

    /**
     * Checks if the given image file exceeds our downscale threshold, and if so,
     * downscales it in memory and returns the bytes of the downscaled image.
     * Note that the returned byte array will always be JPEG image data!
     * Update your mime type accordingly if this method returns non-null.
     */
    private static byte[] downscaleIfNecessary(File imageFile) throws Exception {
        long downscaleThreshold = IceExtension.getLLMDownscaleThresholdBytes();
        if (imageFile.length() <= downscaleThreshold) {
            return null; // No downscale needed, caller can just read the original file bytes
        }

        BufferedImage scaledImage = null;
        byte[] imageBytes;
        try {
            BufferedImage original = ImageUtil.loadImage(imageFile);
            final int maxDimension = 2048; // Arbitrary choice here, but seems reasonable
            int origW = original.getWidth();
            int origH = original.getHeight();
            double scaleRatio = Math.min(1.0, (double)maxDimension / Math.max(origW, origH));
            int newW = Math.max(1, (int)(origW * scaleRatio));
            int newH = Math.max(1, (int)(origH * scaleRatio));

            // Scale it in memory:
            scaledImage = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaledImage.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                     RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.drawImage(original, 0, 0, newW, newH, null);
            }
            finally {
                g2d.dispose();
            }
            original.flush();

            // Convert to jpeg with a lower quality setting:
            imageBytes = toJpegBytesWithQuality(scaledImage, 0.75f);

            // Wonky edge case: it might happen that our "downscaled" image is larger
            // than the original. For example, a PNG image with a lot of flat colors.
            // So, if the downscale failed, let's not use it:
            if (imageBytes.length >= imageFile.length()) {
                imageBytes = null; // screw it, let's just pretend this never happened
            }

        }
        finally {
            if (scaledImage != null) {
                scaledImage.flush();
            }
        }

        return imageBytes;
    }

    /**
     * Helper method for downscaling. Always returns jpeg data, even if the input was a png!
     * Make sure to update the mime type accordingly.
     * <p>
     * Should this move to swing-extras and live in the ImageUtil class? Seems awfully specific
     * to this use case...
     * </p>
     *
     * @param image   The image to convert to jpeg bytes.
     * @param quality The jpeg quality to use - recommendation is 0.75f
     */
    private static byte[] toJpegBytesWithQuality(BufferedImage image, float quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) { throw new IllegalStateException("No JPEG writer found"); }

        ImageWriter writer = writers.next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), params);
        }
        finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
