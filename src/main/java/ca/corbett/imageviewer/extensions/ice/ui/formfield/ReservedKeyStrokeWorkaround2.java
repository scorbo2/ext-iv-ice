package ca.corbett.imageviewer.extensions.ice.ui.formfield;

import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.FormFieldGenerationListener;
import ca.corbett.forms.fields.FormField;
import ca.corbett.imageviewer.AppConfig;

/**
 * This class works around a bug in KeyStrokeProperty where all attempts to set
 * a list of reserved keystrokes are ignored. The workaround is to attach a
 * FormFieldGenerationListener to the property, and set the reserved keystrokes
 * list directly on the generated KeyStrokeField.
 * <p>
 * This bug was discovered in swing-extras 2.7, and is being tracked in
 * <a href="https://github.com/scorbo2/swing-extras/issues/322">issue 322</a>.
 * A future release of this extension can remove this workaround once the bug
 * is fixed in swing-extras.
 * </p>
 * <p>
 * Note: we can't use the ReservedKeyStrokeWorkaround class in the
 * application core code because it only works with KeyStrokeField instances,
 * whereas we are working with our own TagHotkeyField class here. This class
 * is otherwise identical to that one.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class ReservedKeyStrokeWorkaround2 implements FormFieldGenerationListener {
    @Override
    public void formFieldGenerated(AbstractProperty property, FormField formField) {
        if (!(formField instanceof TagHotkeyField hotkeyField)) {
            return;
        }

        hotkeyField.setReservedKeyStrokes(AppConfig.RESERVED_KEYSTROKES);
    }
}

