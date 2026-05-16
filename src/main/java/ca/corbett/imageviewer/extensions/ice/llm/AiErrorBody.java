package ca.corbett.imageviewer.extensions.ice.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * If we get anything other than 200 OK, we'll expect an error response body
 * in this format. All we really care about is the code and the error message,
 * so we can display something hopefully useful to the user.
 * <p>
 * Example error response body:
 * </p>
 * <pre>
 * {
 *   "error": {
 *     "code": 400,
 *     "message": "Failed to load image or audio file",
 *     "type": "invalid_request_error"
 *   }
 * }
 * </pre>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiErrorBody {
    private ErrorNode error;

    /**
     * Returns the numeric error code, if we were able to parse it, otherwise returns -1.
     */
    public int getCode() {
        if (error == null) {
            return -1;
        }
        return error.code;
    }

    /**
     * Returns the included error text, if we were able to parse it, otherwise returns "Unknown error".
     */
    public String getMessage() {
        if (error == null) {
            return "Unknown error";
        }
        return error.message;
    }

    /**
     * Returns the supplied error type, if we were able to parse it, otherwise returns "unknown_error".
     */
    public String getType() {
        if (error == null) {
            return "unknown_error";
        }
        return error.type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorNode {
        private int code;
        private String message;
        private String type;
    }
}
