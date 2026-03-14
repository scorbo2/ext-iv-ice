package ca.corbett.imageviewer.extensions.ice.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ice.IceExtension;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;

import java.awt.event.ActionEvent;

/**
 * An action to toggle the visibility of the quick tag panels by inverting the current layout.
 * If panels are hidden, both are shown; if only the left is shown, it switches to right; if only
 * the right is shown, it switches to left; and if both are shown, both are hidden.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class QuickTagToggleLeftRightAction extends EnhancedAction {

    private static QuickTagToggleLeftRightAction instance;

    private QuickTagToggleLeftRightAction() {
        super("Quick tag toggle left+right");
    }

    public static QuickTagToggleLeftRightAction getInstance() {
        if (instance == null) {
            instance = new QuickTagToggleLeftRightAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Get the property that controls quick tag panel position:
        AbstractProperty prop = AppConfig
                .getInstance()
                .getPropertiesManager()
                .getProperty(IceExtension.quickTagPanelPositionProp);

        if (prop instanceof ComboProperty<?> comboProp) {
            // Option indexes:
            //   0 == hidden
            //   1 == left
            //   2 == right
            //   3 == both left and right
            //
            // Basically, invert whatever is showing:
            int newIndex = switch (comboProp.getSelectedIndex()) {
                case 0 -> 3; // hidden -> both
                case 1 -> 2; // left -> right
                case 2 -> 1; // right -> left
                case 3 -> 0; // both -> none
                default -> 0; // should not happen
            };

            comboProp.setSelectedIndex(newIndex);
            AppConfig.getInstance().save(); // force silent save to persist this immediately

            // It may seem like overkill to reload the entire UI just to hide these panels,
            // but the main image view needs to be laid out again as a result of this change.
            ReloadUIAction.getInstance().actionPerformed(null);
        }

        // Should never happen...
        else {
            MainWindow.getInstance().showMessageDialog("Internal Error", "Configuration property type mismatch.");
        }
    }
}
