package ca.corbett.imageviewer.extensions.ice.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * To make it possible to parse the response json via Jackson,
 * we need to have a class that maps out the expected schema.
 * Since this is a very simple use case, we won't map every possible
 * field, just the output array and its content subarray.
 * In the best case, we're looking for response.output[0].content[0].text,
 * but it's possible that we'll get more than one output object, and/or
 * more than one content object within each output object.
 * <p>
 * <b>NOTE:</b> This is from the newer "responses" API. Currently, we're
 * still using the older "chat completions" API. Consider this class for future
 * migration to the newer API. But for now, use AiCompletionsBody instead.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiResponseBody {
    private String status;
    private OutputNode[] output;
    private String error;

    /**
     * The status returned by the LLM.
     * Typically this will be "completed".
     * We return this as-is.
     */
    public String getStatus() {
        return status;
    }

    /**
     * An optional error message returned by the LLM.
     * Typically this will be null.
     * We return this as-is.
     */
    public String getError() {
        return error;
    }

    /**
     * This will return a concatenation of all the text fields in the content arrays of all the output objects.
     * The returned value may make absolutely no sense, if we got more than one output node.
     * I don't really know what I'm doing here, lol.
     * Typically, if all went well, we just end up returning output[0].content[0].text, but we want to be
     * able to handle unexpected output without throwing exceptions.
     */
    public String getOutput() {
        if (output == null || output.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OutputNode outputNode : output) {
            if (outputNode.content != null) {
                for (ContentNode contentNode : outputNode.content) {
                    if (contentNode.text != null) {
                        sb.append(contentNode.text);
                    }
                }
            }
        }
        return sb.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputNode {
        private String type;
        private ContentNode[] content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentNode {
        private String type;
        private String text;
    }
}
