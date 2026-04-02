package loci.plugins.metaedit;

import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Main dialog window for MetaEdit. Provides the metadata tree view
 * along with controls for toggling edit mode, saving, and reverting.
 */
public class MetaEditDialog extends JFrame {

	private final ImagePlus imp;
	private final MetadataNode root;
	private final List<MetadataTreePanel> treePanels = new ArrayList<>();
	private JTabbedPane tabbedPane;

	private JToggleButton editToggle;
	private JButton saveButton;
	private JButton revertButton;
	private JLabel statusLabel;

	public MetaEditDialog(ImagePlus imp, MetadataNode root) {
		super("MetaEdit - Metadata Editor");
		this.imp = imp;
		this.root = root;

		initUI();
	}

	private void initUI() {
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout(5, 5));

		// -- Header panel --
		JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		String imageName = imp != null ? imp.getTitle() : "Unknown";
		JLabel titleLabel = new JLabel("Image: " + imageName);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		headerPanel.add(titleLabel);
		add(headerPanel, BorderLayout.NORTH);

		// -- Tabbed metadata panels --
		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		for (MetadataNode category : root.getChildren()) {
			MetadataTreePanel panel = new MetadataTreePanel(category);
			treePanels.add(panel);
			tabbedPane.addTab(category.getName(), panel);
		}
		add(tabbedPane, BorderLayout.CENTER);

		// -- Button panel --
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		editToggle = new JToggleButton("Enable Editing");
		editToggle.addActionListener(this::onToggleEdit);
		buttonPanel.add(editToggle);

		revertButton = new JButton("Revert All");
		revertButton.setEnabled(false);
		revertButton.addActionListener(this::onRevert);
		buttonPanel.add(revertButton);

		saveButton = new JButton("Apply & Save");
		saveButton.setEnabled(false);
		saveButton.addActionListener(this::onSave);
		buttonPanel.add(saveButton);

		// -- Status bar --
		JPanel bottomPanel = new JPanel(new BorderLayout());
		statusLabel = new JLabel(" ");
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);
		bottomPanel.add(buttonPanel, BorderLayout.NORTH);

		add(bottomPanel, BorderLayout.SOUTH);

		// -- Final setup --
		pack();
		setMinimumSize(new Dimension(550, 450));
		setLocationRelativeTo(null);
	}

	private void onToggleEdit(ActionEvent e) {
		boolean editing = editToggle.isSelected();
		for (MetadataTreePanel panel : treePanels) {
			panel.setEditMode(editing);
		}
		editToggle.setText(editing ? "Disable Editing" : "Enable Editing");
		saveButton.setEnabled(editing);
		revertButton.setEnabled(editing);
		updateStatus();
	}

	private void onRevert(ActionEvent e) {
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Revert all changes to original values?",
			"Confirm Revert",
			JOptionPane.YES_NO_OPTION
		);
		if (choice == JOptionPane.YES_OPTION) {
			root.revertAll();
			for (MetadataTreePanel panel : treePanels) {
				panel.refresh();
			}
			updateStatus();
		}
	}

	private void onSave(ActionEvent e) {
		if (!root.hasModifications()) {
			JOptionPane.showMessageDialog(this,
				"No changes to save.", "Save", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		int choice = JOptionPane.showConfirmDialog(
			this,
			"Apply changes to the image and save companion metadata file?",
			"Confirm Save",
			JOptionPane.YES_NO_OPTION
		);
		if (choice != JOptionPane.YES_OPTION) return;

		MetadataWriter writer = new MetadataWriter();
		String savedPath = writer.save(imp, root);

		root.acceptAll();
		for (MetadataTreePanel panel : treePanels) {
			panel.refresh();
		}

		if (savedPath != null) {
			statusLabel.setText("Applied & saved: " + savedPath);
			JOptionPane.showMessageDialog(this,
				"Changes applied to image.\nCompanion file saved to:\n" + savedPath,
				"Save Successful", JOptionPane.INFORMATION_MESSAGE);
		}
		else {
			statusLabel.setText("Changes applied to image (no companion file written).");
			JOptionPane.showMessageDialog(this,
				"Changes applied to the image in memory.\n" +
				"No companion file was written (image may not have a file path).",
				"Applied", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void updateStatus() {
		if (root.hasModifications()) {
			statusLabel.setText("Unsaved changes");
		}
		else {
			statusLabel.setText(" ");
		}
	}

}
