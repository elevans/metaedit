package loci.plugins.metaedit;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A JPanel that displays {@link MetadataNode} data in a two-column
 * table (Key | Value) with light-gray grid lines. Group nodes appear
 * as section-header rows; leaf nodes appear as key-value rows.
 * Double-click a value cell to edit (when edit mode is enabled).
 */
public class MetadataTreePanel extends JPanel {

	private static final Color GRID_COLOR = new Color(210, 210, 210);
	private static final Color MODIFIED_COLOR = new Color(0, 100, 200);
	private static final Color READONLY_COLOR = Color.GRAY;
	private static final Color SECTION_BG = new Color(240, 240, 240);

	private final MetadataNode rootNode;
	private final List<Row> rows = new ArrayList<>();
	private final MetadataTableModel tableModel;
	private final JTable table;
	private boolean editMode = false;

	public MetadataTreePanel(MetadataNode rootNode) {
		this.rootNode = rootNode;
		setLayout(new BorderLayout());

		buildRows();

		tableModel = new MetadataTableModel();
		table = new JTable(tableModel);
		table.setRowHeight(24);
		table.setGridColor(GRID_COLOR);
		table.setShowGrid(true);
		table.setIntercellSpacing(new Dimension(1, 1));
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);

		table.getColumnModel().getColumn(0).setPreferredWidth(180);
		table.getColumnModel().getColumn(1).setPreferredWidth(320);

		table.setDefaultRenderer(Object.class, new MetadataCellRenderer());

		// Double-click to edit value cells
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && editMode) {
					int viewRow = table.rowAtPoint(e.getPoint());
					int viewCol = table.columnAtPoint(e.getPoint());
					if (viewRow >= 0 && viewCol == 1) {
						editRowValue(viewRow);
					}
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(500, 400));
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Flatten the metadata node tree into table rows.
	 * Children of the root become either leaf rows or section headers.
	 */
	private void buildRows() {
		rows.clear();
		for (MetadataNode child : rootNode.getChildren()) {
			addNodeRows(child, 0);
		}
	}

	private void addNodeRows(MetadataNode node, int depth) {
		if (node.isLeaf()) {
			rows.add(new Row(node, depth, false));
		} else {
			rows.add(new Row(node, depth, true));
			for (MetadataNode child : node.getChildren()) {
				addNodeRows(child, depth + 1);
			}
		}
	}

	private void editRowValue(int viewRow) {
		Row row = rows.get(viewRow);
		if (row.isSection || !row.node.isEditable() || !row.node.isLeaf()) return;

		String currentValue = row.node.getValue();
		String newValue = JOptionPane.showInputDialog(
			this,
			"Edit value for: " + row.node.getName(),
			currentValue
		);

		if (newValue != null && !newValue.equals(currentValue)) {
			row.node.setValue(newValue);
			tableModel.fireTableRowsUpdated(viewRow, viewRow);
		}
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
		table.repaint();
	}

	public void refresh() {
		buildRows();
		tableModel.fireTableDataChanged();
	}

	// -- Row data holder --

	private static class Row {
		final MetadataNode node;
		final int depth;
		final boolean isSection;

		Row(MetadataNode node, int depth, boolean isSection) {
			this.node = node;
			this.depth = depth;
			this.isSection = isSection;
		}
	}

	// -- Table model --

	private class MetadataTableModel extends AbstractTableModel {

		private static final String[] COLUMNS = {"Key", "Value"};

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Row row = rows.get(rowIndex);
			if (row.isSection) {
				return columnIndex == 0 ? row.node.getName() : "";
			}
			return columnIndex == 0 ? row.node.getName() : row.node.getValue();
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false; // editing handled via double-click dialog
		}
	}

	// -- Cell renderer --

	private class MetadataCellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean isSelected, boolean hasFocus,
			int rowIdx, int column)
		{
			Component comp = super.getTableCellRendererComponent(
				table, value, isSelected, hasFocus, rowIdx, column);

			Row row = rows.get(rowIdx);

			// Reset defaults
			setFont(table.getFont());
			setForeground(table.getForeground());
			setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

			if (row.isSection) {
				// Section header styling
				setFont(table.getFont().deriveFont(Font.BOLD));
				if (!isSelected) {
					setBackground(SECTION_BG);
				}
				// Indent nested sections
				if (column == 0 && row.depth > 0) {
					setText("  ".repeat(row.depth) + row.node.getName());
				}
			} else {
				// Leaf key-value row
				if (column == 0) {
					// Indent key based on depth
					String indent = row.depth > 0 ? "  ".repeat(row.depth) : "";
					setText(indent + row.node.getName());
				}

				if (column == 1 && row.node.isModified()) {
					setForeground(MODIFIED_COLOR);
					setFont(table.getFont().deriveFont(Font.BOLD));
				} else if (!row.node.isEditable()) {
					setForeground(READONLY_COLOR);
				}

				// Tooltip hint in edit mode
				if (editMode && row.node.isEditable() && column == 1) {
					setToolTipText("Double-click to edit");
				} else {
					setToolTipText(null);
				}
			}

			return comp;
		}
	}
}
