package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;

import java.util.logging.Logger;

/**
 * Attach this validator to any ShortTextField or LongTextField to ensure that the given
 * input is a valid, non-empty tag list according to the rules of our TagList class.
 * See TagList.isValidNonEmptyTagString().
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class TagListValidator implements FieldValidator<FormField> {
    private static final Logger log = Logger.getLogger(TagListValidator.class.getName());

    @Override
    public ValidationResult validate(FormField fieldToValidate) {
        if (fieldToValidate == null) {
            log.warning("Null field passed to TagListValidator; this should not happen. Ignoring.");
            return ValidationResult.valid(); // just ignore nonsense input, shouldn't happen anyway
        }

        String text = null;
        if (fieldToValidate instanceof ShortTextField textField) {
            text = textField.getText();
        }
        else if (fieldToValidate instanceof LongTextField textField) {
            text = textField.getText();
        }

        if (text == null) {
            log.warning("Unexpected field type passed to TagListValidator: "
                                + fieldToValidate.getClass().getName()
                                + "; expected LongTextField or ShortTextField.");
            return ValidationResult.valid(); // unexpected field type; just pass it.
        }

        // We have to handle this separately because isValidNonEmptyTagString() will simply
        // return false if the input is blank OR invalid, so we won't be able to tell what went wrong.
        if (text.isBlank()) {
            return ValidationResult.invalid("Tag list cannot be blank.");
        }

        // Now we can rely on TagList to check for invalid characters and formatting:
        if (!TagList.isValidNonEmptyTagString(text)) {
            return ValidationResult.invalid(TagList.TAG_FORMAT_ERROR);
        }

        return ValidationResult.valid();
    }
}
