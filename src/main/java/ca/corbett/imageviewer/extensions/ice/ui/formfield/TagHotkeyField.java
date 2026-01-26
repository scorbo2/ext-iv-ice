package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;

import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * A custom FormField implementation for displaying and editing the
 * combination of a hotkey (keyboard shortcut) and a tag string
 * to be associated with that hotkey. The idea is that you can
 * assign commonly-used tags to hotkeys for very quick application
 * to images.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2.4.0
 */
public class TagHotkeyField extends FormField {

    private JTextField hotkeyField;
    private JTextField tagField;

    public TagHotkeyField() {
        // TODO this change has been pulled from the 2.4 release.
        //      Revisit this for ImageViewer 2.5.
        //      All that's left to do is combine the two text fields
        //      into one wrapper panel and add our custom validator to it.
        //      Then, add a Property wrapper around it, add it to app config,
        //      and register the key handler.
    }

    /**
     * Used internally to validate the field automatically.
     * Our validation constraints:
     * <ul>
     *     <li>The hotkey field is optional. It can be blanked out.
     *         However, if the tagField has a value, then the hotkey
     *         field must also have a value.</li>
     *      <li>If the hotkey field has a value, it must be a valid,
     *         parseable shortcut string, as defined by KeyStrokeManager.</li>
     *      <li>The tagField is optional. It can be blanked out.</li>
     *      <li>If tagField has a value, it must be a valid tag string
     *         as defined by TagList.isValidTagString().</li>
     * </ul>
     */
    private static class HotkeyValidator implements FieldValidator<TagHotkeyField> {

        private static final String TAG_REQUIRES_HOTKEY = "If a tag is specified, a hotkey must also be specified.";
        private static final String INVALID_KEYSTROKE = "The specified hotkey is not a valid keystroke string.";

        @Override
        public ValidationResult validate(TagHotkeyField fieldToValidate) {
            String tagText = fieldToValidate.tagField.getText();
            String hotkeyText = fieldToValidate.hotkeyField.getText();

            // Check tagField first:
            if (!tagText.isBlank()) {
                if (!TagList.isValidTagString(tagText)) {
                    return ValidationResult.invalid(TagList.TAG_FORMAT_ERROR);
                }

                if (hotkeyText.isBlank()) {
                    return ValidationResult.invalid(TAG_REQUIRES_HOTKEY);
                }
            }

            if (!hotkeyText.isBlank()) {
                // This won't work! ImageViewer 2.4 is still on swing-extras 2.6,
                // which doesn't have KeyStrokeManager.
                // So, I'm going to park this branch until ImageViewer 2.5.
                // TODO revisit this
                KeyStroke parsed = KeyStrokeManager.parseKeyStroke(hotkeyText);
                if (parsed == null) {
                    return ValidationResult.invalid(INVALID_KEYSTROKE);
                }
            }

            return ValidationResult.valid();
        }
    }
}
