package ca.corbett.imageviewer.extensions.ice.llm;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AiErrorBody {
    private ErrorNode error;

    /**
     * "Internal" here does not refer to 500 Internal Server Error, but rather
     * to any error that occurs within this extension, including when we
     * are unable to parse the error response body from the LLM.
     */
    public static final int INTERNAL_ERROR = -1;

    /**
     * Static convenience method to create an AiErrorBody from the given code, error type, and message.
     * The errorCode in that case will be INTERNAL_ERROR.
     */
    public static AiErrorBody of(String errorType, String message) {
        return of(INTERNAL_ERROR, errorType, message);
    }

    /**
     * If you actually have an error code, you can use this method to create an AiErrorBody with that code,
     * along with the error type and message.
     */
    public static AiErrorBody of(int errorCode, String errorType, String message) {
        AiErrorBody body = new AiErrorBody();
        ErrorNode errorNode = new ErrorNode();
        errorNode.code = errorCode;
        errorNode.type = errorType;
        errorNode.message = message;
        body.error = errorNode;
        return body;
    }

    /**
     * Returns the numeric error code, if we were able to parse it, otherwise returns -1.
     */
    public int getCode() {
        if (error == null) {
            return INTERNAL_ERROR;
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
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class ErrorNode {
        private int code;
        private String message;
        private String type;
    }
}
