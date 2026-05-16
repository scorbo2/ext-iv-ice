package ca.corbett.imageviewer.extensions.ice.llm;

/**
 * If we get anything other than 200 OK, we'll expect an error response body
 * in this format. All we really care about is the code and the error message,
 * so we can display something hopefully useful to the user.
 * <p>
 * Example error response body:
 * </p>
 * <pre>
 *     {"error":{"code":400,"message":"Failed to load image or audio file","type":"invalid_request_error"}}
 * </pre>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class AiErrorBody {
    private ErrorNode errorNode;

    public int getCode() {
        if (errorNode == null) {
            return -1;
        }
        return errorNode.code;
    }

    public String getMessage() {
        if (errorNode == null) {
            return "Unknown error";
        }
        return errorNode.message;
    }

    public String getType() {
        if (errorNode == null) {
            return "unknown_error";
        }
        return errorNode.type;
    }

    private class ErrorNode {
        private int code;
        private String message;
        private String type;
    }
}
