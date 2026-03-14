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
 * An action to toggle the visibility of the quick tag panel on the right side.
 * If the right panel is currently visible, it will be hidden; if it is hidden, it will be shown.
 * If there is a quick tag panel also in the left position, it will remain visible.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 3.0.0
 */
public class QuickTagToggleRightAction extends EnhancedAction {

    private static QuickTagToggleRightAction instance;

    private QuickTagToggleRightAction() {
        super("Quick tag toggle right");
    }

    public static QuickTagToggleRightAction getInstance() {
        if (instance == null) {
            instance = new QuickTagToggleRightAction();
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
            // Basically, if right was showing, hide it, otherwise show it:
            int newIndex = switch (comboProp.getSelectedIndex()) {
                case 0 -> 2; // hidden -> right
                case 2 -> 0; // right -> hidden
                case 1 -> 3; // left -> both
                case 3 -> 1; // both -> left
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
