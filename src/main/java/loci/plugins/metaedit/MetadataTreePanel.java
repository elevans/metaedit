package loci.plugins.metaedit;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A JPanel that displays a {@link MetadataNode} tree using JTree.
 * Leaf value nodes can be edited by double-clicking.
 */
public class MetadataTreePanel extends JPanel {

	private final JTree tree;
	private final DefaultTreeModel treeModel;
	private boolean editMode = false;

	public MetadataTreePanel(MetadataNode rootNode) {
		setLayout(new BorderLayout());

		DefaultMutableTreeNode treeRoot = buildSwingTree(rootNode);
		treeModel = new DefaultTreeModel(treeRoot);
		tree = new JTree(treeModel);

		tree.setCellRenderer(new MetadataCellRenderer());
		tree.setRowHeight(22);
		tree.setEditable(false);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		// Expand all nodes by default
		expandAll();

		// Double-click to edit
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && editMode) {
					TreePath path = tree.getPathForLocation(e.getX(), e.getY());
					if (path != null) {
						editNodeAtPath(path);
					}
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(tree);
		scrollPane.setPreferredSize(new Dimension(500, 400));
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Build a Swing DefaultMutableTreeNode tree from our MetadataNode.
	 */
	private DefaultMutableTreeNode buildSwingTree(MetadataNode node) {
		DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(node);
		for (MetadataNode child : node.getChildren()) {
			swingNode.add(buildSwingTree(child));
		}
		return swingNode;
	}

	/**
	 * Expand all tree nodes.
	 */
	private void expandAll() {
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	/**
	 * Open an inline editing dialog for a leaf node.
	 */
	private void editNodeAtPath(TreePath path) {
		DefaultMutableTreeNode swingNode = (DefaultMutableTreeNode) path.getLastPathComponent();
		MetadataNode metaNode = (MetadataNode) swingNode.getUserObject();

		if (!metaNode.isEditable() || !metaNode.isLeaf()) return;

		String currentValue = metaNode.getValue();
		String newValue = JOptionPane.showInputDialog(
			this,
			"Edit value for: " + metaNode.getName(),
			currentValue
		);

		if (newValue != null && !newValue.equals(currentValue)) {
			metaNode.setValue(newValue);
			treeModel.nodeChanged(swingNode);
		}
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
		tree.repaint();
	}

	/**
	 * Refresh the tree display after external changes.
	 */
	public void refresh() {
		treeModel.reload();
		expandAll();
	}

	public JTree getTree() {
		return tree;
	}

	/**
	 * Custom cell renderer that highlights modified values and
	 * distinguishes editable from read-only fields.
	 */
	private class MetadataCellRenderer extends DefaultTreeCellRenderer {

		private final Color MODIFIED_COLOR = new Color(0, 100, 200);
		private final Color READONLY_COLOR = Color.GRAY;

		@Override
		public Component getTreeCellRendererComponent(
			JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean hasFocus)
		{
			Component comp = super.getTreeCellRendererComponent(
				tree, value, selected, expanded, leaf, row, hasFocus);

			if (value instanceof DefaultMutableTreeNode) {
				DefaultMutableTreeNode swingNode = (DefaultMutableTreeNode) value;
				Object userObj = swingNode.getUserObject();

				if (userObj instanceof MetadataNode) {
					MetadataNode metaNode = (MetadataNode) userObj;

					if (metaNode.isLeaf() && metaNode.getValue() != null) {
						String display = metaNode.getName() + ": " + metaNode.getValue();
						setText(display);

						if (metaNode.isModified()) {
							setForeground(MODIFIED_COLOR);
							setFont(getFont().deriveFont(Font.BOLD));
						}
						else if (!metaNode.isEditable()) {
							setForeground(READONLY_COLOR);
						}
					}
					else {
						setText(metaNode.getName());
						if (!metaNode.isEditable() && metaNode.isLeaf()) {
							setForeground(READONLY_COLOR);
						}
					}

					// Show edit cursor hint when in edit mode
					if (editMode && metaNode.isEditable() && metaNode.isLeaf()) {
						setToolTipText("Double-click to edit");
					}
					else {
						setToolTipText(null);
					}
				}
			}

			return comp;
		}
	}
}
