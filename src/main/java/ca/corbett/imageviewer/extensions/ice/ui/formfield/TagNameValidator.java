package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.fields.LongTextField;
import ca.corbett.forms.fields.ShortTextField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;

import java.util.logging.Logger;

/**
 * A FieldValidator implementation that can ensure that user-supplied tag names
 * are valid according to the rules of our TagList class.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class TagNameValidator implements FieldValidator<FormField> {
    private static final Logger log = Logger.getLogger(TagNameValidator.class.getName());

    private final TagList sourceList;

    public TagNameValidator() {
        this(null);
    }

    public TagNameValidator(TagList sourceList) {
        this.sourceList = sourceList;
    }

    @Override
    public ValidationResult validate(FormField fieldToValidate) {
        if (fieldToValidate == null) {
            log.warning("Null field passed to TagNameValidator; this should not happen. Ignoring.");
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
            log.warning("Unexpected field type passed to TagNameValidator: "
                                + fieldToValidate.getClass().getName()
                                + "; expected LongTextField or ShortTextField.");
            return ValidationResult.valid(); // unexpected field type; just pass it.
        }

        if (text.isBlank()) {
            return ValidationResult.invalid("Tag name cannot be blank.");
        }
        text = text.trim();

        // Check for forbidden characters:
        for (char c : text.toCharArray()) {
            if (TagList.DISALLOWED_TAG_CHARS.contains(c)) {
                return ValidationResult.invalid("Characters { } | , are not allowed in tags.");
            }
        }

        // If we were given a source list, make sure this tag is not already in there:
        if (sourceList != null) {
            if (sourceList.hasTag(text)) {
                return ValidationResult.invalid("Tag \"" + text + "\" already exists.");
            }
        }

        // If we make it here, all good:
        return ValidationResult.valid();
    }
}
