package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A simple FieldValidator that can be attached to any ShortTextField to ensure that the
 * field's value is either blank or a valid URL. If blank values are not permitted,
 * you can combine this with a NonBlankFieldValidator.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class UrlValidator implements FieldValidator<ShortTextField> {

    public static final String MESSAGE = "Value must be a valid URL";

    @Override
    public ValidationResult validate(ShortTextField fieldToValidate) {
        String currentValue = fieldToValidate.getText() == null ? "" : fieldToValidate.getText().trim();
        if (currentValue.isEmpty()) {
            return ValidationResult.valid(); // blank value is fine
        }

        return isValidUrl(currentValue) ? ValidationResult.valid() : ValidationResult.invalid(MESSAGE);
    }

    /**
     * A public convenience method for validating URLs.
     * A "valid" URL for our purpose is one that:
     * <ol>
     *     <li>Can be parsed by the java.net.URL class without throwing a MalformedURLException</li>
     *     <li>Is either HTTP or HTTPS, as those are the only protocols accepted by OpenAI-compatible servers.</li>
     * </ol>
     *
     * @param url A URL in string form.
     * @return true if both of the above mentioned conditions are met.
     */
    public static boolean isValidUrl(String url) {
        try {
            URL actualUrl = new URL(url);
            return actualUrl.getProtocol().equalsIgnoreCase("http")
                    || actualUrl.getProtocol().equalsIgnoreCase("https");
        }
        catch (MalformedURLException ignored) {
            return false;
        }
    }
}
