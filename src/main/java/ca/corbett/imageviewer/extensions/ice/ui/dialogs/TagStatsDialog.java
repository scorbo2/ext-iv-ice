package ca.corbett.imageviewer.extensions.ice.ui.dialogs;

import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.imageviewer.extensions.ice.TagIndex;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Shows statistics on the current tag index, and offers a way to discard
 * and rebuild it.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2.3.0
 */
public class TagStatsDialog extends JDialog {

    public TagStatsDialog() {
        super(MainWindow.getInstance(), "Tag index statistics", true);
        setSize(new Dimension(600,240));
        setLocationRelativeTo(MainWindow.getInstance());
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    public void clearIndex() {
        if (JOptionPane.showConfirmDialog(this,
                                          "Are you sure you wish to clear the tag index?",
                                          "Confirm",
                                          JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) {
            return;
        }

        TagIndex.getInstance().clear();
        TagIndex.getInstance().save();
        JOptionPane.showMessageDialog(this,
                                      "Tag index cleared! You can re-scan directories"
                                        +"\nto build it back up.",
                                      "Tag index cleared",
                                      JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private FormPanel buildFormPanel() {
        FormPanel formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(16);
        formPanel.add(new LabelField("Tag index enabled:", Boolean.toString(TagIndex.isEnabled())));
        formPanel.add(new LabelField("Number of entries:", Integer.toString(TagIndex.getInstance().size())));
        String fileSize = FileSystemUtil.getPrintableSize(TagIndex.getInstance().fileSize());
        formPanel.add(new LabelField("Tag file size:", fileSize));

        TagList popularTags = new TagList();
        popularTags.addAll(TagIndex.getInstance().getMostFrequentTags(5, List.of("square", "landscape", "portrait")));
        formPanel.add(new LabelField("Popular tags:", popularTags.toString()));

        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton button = new JButton("Clear");
        button.addActionListener(e -> clearIndex());
        button.setPreferredSize(new Dimension(110,24));
        panel.add(button);

        button = new JButton("OK");
        button.addActionListener(e -> dispose());
        button.setPreferredSize(new Dimension(110,24));
        panel.add(button);

        return panel;
    }
}
