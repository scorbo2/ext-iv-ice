package ca.corbett.imageviewer.extensions.ice.ui;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.extensions.ice.TagList;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuickTagPanel extends JPanel {

    private static final Logger log = Logger.getLogger(QuickTagPanel.class.getName());

    private final TagList tagList;

    public QuickTagPanel() {
        tagList = TagList.fromFile(new File(Version.SETTINGS_DIR, "quickTags.ice"));
        reset();
    }

    public void reset() {
        removeAll();
        setLayout(new GridBagLayout());
        int rowNumber = 1;
        addLabel("Quick tags", 0);
        for (String tag : tagList.getTags()) {
            addButton(tag, rowNumber++);
        }
        addSpacer(rowNumber++);
        addAddButton(rowNumber++);
        addBottomSpacer(rowNumber);
    }

    private void executeTagAction(String tag) {
        ImageInstance image = MainWindow.getInstance().getSelectedImage();
        if (! image.isEmpty()) {
            File tagFile = new File(image.getImageFile().getParentFile(),
                                    FilenameUtils.getBaseName(image.getImageFile().getName())+".ice");
            TagList imageTags = TagList.fromFile(tagFile);
            if (imageTags.hasTag(tag)) {
                imageTags.remove(tag);
            }
            else {
                imageTags.add(tag);
            }
            imageTags.save();
            ImageViewerExtensionManager.getInstance().imageSelected(image);
        }
    }

    private void addLabel(String text, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(12,12,16,2);
        JLabel label = new JLabel(" " + text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setOpaque(true);
        label.setBackground(LookAndFeelManager.getLafColor("TextArea.selectionBackground", Color.BLUE));
        label.setForeground(LookAndFeelManager.getLafColor("TextArea.selectionForeground", Color.WHITE));
        add(label, gbc);
    }

    private void addSpacer(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,12,0,2);
        JLabel label = new JLabel(" ");
        label.setOpaque(true);
        add(label, gbc);
    }

    private void addButton(String text, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,12,0,2);
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(200, 25));
        button.addActionListener(e -> executeTagAction(text));
        add(button, gbc);
    }

    private void addAddButton(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,12,0,2);
        JButton button = new JButton("Add...");
        button.setPreferredSize(new Dimension(200, 25));
        button.addActionListener(e -> addTagOption());
        add(button, gbc);

    }

    private void addBottomSpacer(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 2;
        JLabel label = new JLabel("");
        add(label, gbc);
    }

    private void addTagOption() {
        String input = JOptionPane.showInputDialog(MainWindow.getInstance(), "Enter new tag:");
        if (input != null) {
            tagList.add(input);
            tagList.save();
            reset();
        }
    }
}
