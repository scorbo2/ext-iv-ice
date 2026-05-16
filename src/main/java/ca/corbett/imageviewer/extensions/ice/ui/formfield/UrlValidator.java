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
        try {
            new URL(currentValue);
            return ValidationResult.valid();
        }
        catch (MalformedURLException ignored) {
            return ValidationResult.invalid(MESSAGE);
        }
    }
}
