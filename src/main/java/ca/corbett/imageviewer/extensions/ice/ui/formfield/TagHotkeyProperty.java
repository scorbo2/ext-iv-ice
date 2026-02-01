package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.extras.properties.Properties;
import ca.corbett.forms.fields.FormField;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.extensions.ice.actions.TagHotkeyAction;

import javax.swing.KeyStroke;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This property extends KeyStrokeProperty to add support for
 * associating a TagList with the hotkey. This allows users
 * to define hotkeys that apply specific tags to images.
 * <p>
 * The associated TagList is persisted along with the keystroke
 * when saving/loading the property to/from Properties.
 * </p>
 * <p>
 * All of the parent class's functionality is preserved, including
 * support for reserved keystrokes and generating a FormField for
 * editing the keystroke. However, we override the FormField generation
 * methods to use our custom TagHotkeyField instead of a KeyStrokeField,
 * so that both the keystroke and tag list can be edited together.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class TagHotkeyProperty extends KeyStrokeProperty {

    private static final Logger log = Logger.getLogger(TagHotkeyProperty.class.getName());
    private final TagHotkeyAction hotkeyAction;

    /**
     * Creates a new TagHotkeyProperty with the given name and label, and no initial keystroke.
     * The associated TagList will be empty initially.
     */
    public TagHotkeyProperty(String fullyQualifiedName, String label) {
        this(fullyQualifiedName, label, null);
    }

    /**
     * Creates a new TagHotkeyProperty with the given name, label, and initial keystroke.
     * The associated TagList will be empty initially.
     */
    public TagHotkeyProperty(String fullyQualifiedName, String label, KeyStroke keyStroke) {
        super(fullyQualifiedName, label, keyStroke, new TagHotkeyAction(label));
        hotkeyAction = (TagHotkeyAction)getAction();
    }

    /**
     * Sets the list of tags to be used in this field, given a tag list string.
     */
    public TagHotkeyProperty setTagList(String tagListString) {
        hotkeyAction.setTagList(TagList.of(tagListString));
        return this;
    }

    /**
     * Sets the list of tags to be used in this field.
     */
    public TagHotkeyProperty setTagList(TagList tagList) {
        hotkeyAction.setTagList(tagList);
        return this;
    }

    /**
     * Returns the tag list being used in this field. May be empty.
     */
    public TagList getTagList() {
        return hotkeyAction.getTagList();
    }

    /**
     * We override this to also save our tag list along with the keystroke.
     */
    @Override
    public void saveToProps(Properties props) {
        super.saveToProps(props); // Save our keystroke and reserved keystrokes

        // Also save our tag list:
        props.setString(getFullyQualifiedName() + ".tags", getTagList().toString());
    }

    /**
     * We override this to also load our tag list along with the keystroke.
     */
    @Override
    public void loadFromProps(Properties props) {
        super.loadFromProps(props); // Load our keystroke and reserved keystrokes

        // Also load our tag list:
        String tagListString = props.getString(getFullyQualifiedName() + ".tags", getTagList().toString());
        setTagList(TagList.of(tagListString));
    }

    /**
     * We need to completely override the parent class's method here to return
     * our own TagHokeyField instead of a KeyStrokeField.
     * <p>
     * The returned field is equipped with a built-in FieldValidator
     * to ensure that the value is valid, and that the selected hotkey
     * is not on the reserved list.
     * </p>
     *
     * @return A TagHotkeyField with our keystroke and tag list set.
     */
    @Override
    public FormField generateFormFieldImpl() {
        TagHotkeyField field = new TagHotkeyField(propertyLabel);
        field.setKeyStroke(getKeyStroke());
        field.setTagList(getTagList());
        field.setReservedKeyStrokes(getReservedKeyStrokes());
        field.setReservedKeyStrokeMsg(getReservedKeyStrokeMsg());
        return field;
    }

    /**
     * We need to completely override the parent class's method here to
     * extract both the keystroke and tag list from our TagHotkeyField.
     *
     * @param field The TagHotkeyField containing a value for this property.
     */
    @Override
    public void loadFromFormField(FormField field) {
        if (field.getIdentifier() == null
                || !field.getIdentifier().equals(fullyQualifiedName)
                || !(field instanceof TagHotkeyField hotkeyField)) {
            log.log(Level.SEVERE, "TagHotkeyProperty.loadFromFormField: received the wrong field \"{0}\"",
                    field.getIdentifier());
            return;
        }

        setKeyStroke(hotkeyField.getKeyStroke());
        setTagList(hotkeyField.getTagList());
    }
}
