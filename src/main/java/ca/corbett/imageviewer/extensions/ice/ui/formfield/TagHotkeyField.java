package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.forms.fields.FormField;
import ca.corbett.forms.fields.KeyStrokeField;
import ca.corbett.forms.validators.FieldValidator;
import ca.corbett.forms.validators.ValidationResult;
import ca.corbett.imageviewer.extensions.ice.TagList;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom FormField implementation for displaying and editing the
 * combination of a hotkey (keyboard shortcut) and a tag string
 * to be associated with that hotkey. The idea is that you can
 * assign commonly-used tags to hotkeys for very quick application
 * to images.
 * <p>
 *     Our field contains two text fields side-by-side: one for the hotkey,
 *     and one for the tag string. The hotkey field uses the same
 *     format as KeyStrokeManager (e.g. "ctrl+alt+T" or "shift+f1").
 *     The tag string field uses the same format as TagList
 *     (comma-separated case-insensitive tags, e.g. "tag1, tag2, tag3").
 * </p>
 * <p>
 *     This field adds its own FieldValidator to ensure that the
 *     field contents are valid:
 * </p>
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
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class TagHotkeyField extends FormField {

    private final JTextField hotkeyField;
    private final JTextField tagField;
    private String reservedKeyStrokeMsg = KeyStrokeField.RESERVED_MSG;
    private final List<KeyStroke> reservedKeyStrokes = new ArrayList<>();

    public TagHotkeyField(String label) {
        fieldLabel.setText(label);

        hotkeyField = new JTextField(6);
        tagField = new JTextField(10);
        InternalDocumentListener changeListener = new InternalDocumentListener();
        hotkeyField.getDocument().addDocumentListener(changeListener);
        tagField.getDocument().addDocumentListener(changeListener);
        JPanel wrapperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapperPanel.add(hotkeyField);
        wrapperPanel.add(new JLabel("  ")); // cheesy spacer
        wrapperPanel.add(tagField);
        fieldComponent = wrapperPanel;
        addFieldValidator(new HotkeyValidator());
    }

    /**
     * Returns the KeyStroke represented by the hotkey field,
     * or null if the field is blank or invalid.
     */
    public KeyStroke getKeyStroke() {
        String hotkeyText = hotkeyField.getText();
        if (hotkeyText.isBlank()) {
            return null;
        }
        return KeyStrokeManager.parseKeyStroke(hotkeyText); // might be null if invalid
    }

    /**
     * Sets the KeyStroke represented by the hotkey field.
     * If null is given, the field will be blanked out.
     */
    public TagHotkeyField setKeyStroke(KeyStroke keyStroke) {
        if (keyStroke == null) {
            hotkeyField.setText("");
        }
        else {
            hotkeyField.setText(KeyStrokeManager.keyStrokeToString(keyStroke));
        }
        return this;
    }

    /**
     * Returns the TagList represented by the tag field,
     * or an empty TagList if the field is blank.
     */
    public TagList getTagList() {
        String tagText = tagField.getText();
        if (tagText.isBlank()) {
            return new TagList(); // empty tag list
        }
        return TagList.of(tagText);
    }

    /**
     * Sets the TagList for this field, or blanks it out if null
     * or an empty TagList is given.
     */
    public TagHotkeyField setTagList(TagList tagList) {
        if (tagList == null || tagList.isEmpty()) {
            tagField.setText("");
        }
        else {
            tagField.setText(tagList.toString());
        }
        return this;
    }

    /**
     * Optionally set a list of reserved KeyStrokes that cannot be assigned
     * using this field. By default, no KeyStrokes are reserved.
     * The given list will replace any previously set reserved KeyStrokes.
     */
    public TagHotkeyField setReservedKeyStrokes(List<KeyStroke> reservedKeyStrokes) {
        setReservedKeyStrokes(reservedKeyStrokes, KeyStrokeField.RESERVED_MSG);
        return this;
    }

    /**
     * Optionally set a list of reserved KeyStrokes that cannot be assigned,
     * and also sets a custom message to be used when validation fails
     * because a reserved KeyStroke was entered. The given list will replace
     * any previously set reserved KeyStrokes.
     */
    public TagHotkeyField setReservedKeyStrokes(List<KeyStroke> reservedKeyStrokes, String reservedMsg) {
        this.reservedKeyStrokes.clear();

        if (reservedKeyStrokes == null || reservedKeyStrokes.isEmpty()) {
            return this; // nothing to add
        }

        this.reservedKeyStrokeMsg = (reservedMsg == null || reservedMsg.isBlank())
                ? KeyStrokeField.RESERVED_MSG
                : reservedMsg;

        this.reservedKeyStrokes.addAll(reservedKeyStrokes);
        return this;
    }

    /**
     * Returns a copy of the list of reserved KeyStrokes that cannot be assigned
     */
    public List<KeyStroke> getReservedKeyStrokes() {
        return new ArrayList<>(reservedKeyStrokes);
    }

    /**
     * Returns the validation message that will be given if a reserved
     * KeyStroke is entered.
     */
    public String getReservedKeyStrokeMsg() {
        return reservedKeyStrokeMsg;
    }

    /**
     * Sets the validation message that will be given if a reserved
     * KeyStroke is entered.
     */
    public TagHotkeyField setReservedKeyStrokeMsg(String reservedKeyStrokeMsg) {
        this.reservedKeyStrokeMsg = (reservedKeyStrokeMsg == null || reservedKeyStrokeMsg.isBlank())
                ? KeyStrokeField.RESERVED_MSG
                : reservedKeyStrokeMsg;
        return this;
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
    private class HotkeyValidator implements FieldValidator<TagHotkeyField> {

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
                KeyStroke parsed = KeyStrokeManager.parseKeyStroke(hotkeyText);
                if (parsed == null) {
                    return ValidationResult.invalid(INVALID_KEYSTROKE);
                }

                // Make sure it's not reserved:
                for (KeyStroke reserved : reservedKeyStrokes) {
                    if (parsed.equals(reserved)) {
                        return ValidationResult.invalid(reservedKeyStrokeMsg);
                    }
                }
            }

            return ValidationResult.valid();
        }
    }

    /**
     * Listens for changes to either of our text fields, and fires a value
     * changed event when either changes.
     * <p>
     * Note that DocumentListener is a little broken in Java Swing, so you
     * may receive multiple events for a single change. This is an unfortunate
     * known issue.
     * </p>
     */
    private class InternalDocumentListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            fireValueChangedEvent();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            fireValueChangedEvent();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            fireValueChangedEvent();
        }
    }
}
