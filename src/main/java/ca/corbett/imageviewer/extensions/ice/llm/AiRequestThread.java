package ca.corbett.imageviewer.extensions.ice.llm;

import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.imageviewer.extensions.ice.TagList;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;
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

    public AiRequestThread(File imageFile, AiConnectionManager manager, AiConnectionManager.CompletionCallback onComplete) {
        this.manager = manager;
        this.onComplete = onComplete;
        this.imageFile = imageFile;
    }

    @Override
    public void run() {
        // Let's get the progress bar up:
        // (2 steps: base64 encode the image, make the request)
        fireProgressBegins(2);
        try {

            // These were validated by our AiManager, so we won't do it here again:
            URL url = manager.getLlmUrl();
            String model = manager.getLlmModel();
            String apiKey = manager.getLlmApiKey();
            TagList tags = manager.getLlmTags();

            // Base64-encode the image file:
            fireProgressUpdate(0, "Encoding image...");
            Base64.Encoder encoder = Base64.getEncoder();
            String base64ImageData;
            try {
                byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                base64ImageData = encoder.encodeToString(imageBytes);
            }
            catch (Exception e) {
                log.severe("Failed to read and encode image file: " + e.getMessage());
                onComplete.onComplete(noTags());
                return;
            }

            // Do our tag substitution in our template to get the actual request body:
            String jsonBody = prepareRequestBody(model, tags, base64ImageData);

            // Now we can fire off the request and parse the response:
            try {
                fireProgressUpdate(1, "Sending request to LLM...");
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                                .uri(url.toURI())
                                                                .header("Content-Type", "application/json");
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
                        log.severe("LLM request failed with status code " + response.statusCode() + ": "
                                           + errorBody.getMessage() + " (code " + errorBody.getCode() + ", type "
                                           + errorBody.getType() + ")");
                    }
                    catch (Exception e) {
                        // We were unable to parse it, so best we can do is show the generic error code:
                        // (we *could* log the response body, but it might be huge, and it might leak
                        //  server info into the log, so we'll just let it go)
                        log.severe("LLM request failed with status code " + response.statusCode());
                    }
                    onComplete.onComplete(noTags());
                    return;
                }

                // We'll use Jackson to parse the json response:
                AiCompletionsBody responseObject = objectMapper.readValue(response.body(), AiCompletionsBody.class);
                log.info("Auto-tag: received response from LLM: finish_reason=" + responseObject.getFinishReason()
                                 + ", output=" + responseObject.getOutput());
                TagList results = TagList.of(responseObject.getOutput());
                if (results.isEmpty()) {
                    log.warning("LLM response did not contain any tags. Returning NO_TAGS.");
                    onComplete.onComplete(noTags());
                }
                else {
                    onComplete.onComplete(results);
                }
            }
            catch (Exception e) {
                log.severe("Failed to send LLM request or parse response: " + e.getMessage());
                onComplete.onComplete(noTags());
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

    private String prepareRequestBody(String model, TagList tags, String base64ImageData) {
        // Figure out which template we need:
        String template = tags.isEmpty() ? manager.getJsonTemplateTagless() : manager.getJsonTemplate();

        // Handle safe escaping of our String inputs:
        // (very unlikely, but user could technically have a tag with quotation marks in it)
        // (note that we don't worry about the base64 image data, since it should already be json-safe)
        String safeModel = new String(new SerializedString(model).asQuotedChars());
        String safeTags = new String(new SerializedString(tags.toString()).asQuotedChars());

        // Now we can safely do our string replacement to get the final request body:
        return template
                .replace(AiConnectionManager.KEY_MODEL, safeModel)
                .replace(AiConnectionManager.KEY_IMG_DATA, base64ImageData)
                .replace(AiConnectionManager.KEY_TAGS, safeTags);
    }
}
