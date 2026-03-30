package loci.plugins.metaedit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a hierarchical metadata tree. Each node has a name,
 * an optional value (for leaf nodes), and child nodes.
 */
public class MetadataNode {

	private String name;
	private String value;
	private String originalValue;
	private final List<MetadataNode> children = new ArrayList<>();
	private MetadataNode parent;
	private boolean editable;

	public MetadataNode(String name) {
		this(name, null);
	}

	public MetadataNode(String name, String value) {
		this.name = name;
		this.value = value;
		this.originalValue = value;
		this.editable = (value != null);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getOriginalValue() {
		return originalValue;
	}

	public void setOriginalValue(String originalValue) {
		this.originalValue = originalValue;
	}

	public boolean isModified() {
		if (value == null && originalValue == null) return false;
		if (value == null || originalValue == null) return true;
		return !value.equals(originalValue);
	}

	public List<MetadataNode> getChildren() {
		return children;
	}

	public void addChild(MetadataNode child) {
		child.setParent(this);
		children.add(child);
	}

	public MetadataNode getParent() {
		return parent;
	}

	public void setParent(MetadataNode parent) {
		this.parent = parent;
	}

	public boolean isEditable() {
		return editable;
	}

	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	public boolean isLeaf() {
		return children.isEmpty();
	}

	/**
	 * Revert this node's value to its original value.
	 */
	public void revert() {
		this.value = this.originalValue;
	}

	/**
	 * Recursively revert all modified nodes in this subtree.
	 */
	public void revertAll() {
		revert();
		for (MetadataNode child : children) {
			child.revertAll();
		}
	}

	/**
	 * Accept the current value as the new original (after save).
	 */
	public void acceptAll() {
		this.originalValue = this.value;
		for (MetadataNode child : children) {
			child.acceptAll();
		}
	}

	/**
	 * Check if any node in this subtree has been modified.
	 */
	public boolean hasModifications() {
		if (isModified()) return true;
		for (MetadataNode child : children) {
			if (child.hasModifications()) return true;
		}
		return false;
	}

	/**
	 * Collect all modified leaf nodes in this subtree.
	 */
	public List<MetadataNode> getModifiedNodes() {
		List<MetadataNode> modified = new ArrayList<>();
		collectModified(modified);
		return modified;
	}

	private void collectModified(List<MetadataNode> list) {
		if (isLeaf() && isModified()) {
			list.add(this);
		}
		for (MetadataNode child : children) {
			child.collectModified(list);
		}
	}

	/**
	 * Build the full path of this node from root (e.g. "Image/Pixels/SizeX").
	 */
	public String getPath() {
		if (parent == null) return name;
		return parent.getPath() + "/" + name;
	}

	/**
	 * Flatten the tree into a key-value map using paths as keys.
	 */
	public Map<String, String> toFlatMap() {
		Map<String, String> map = new LinkedHashMap<>();
		flattenInto(map);
		return map;
	}

	private void flattenInto(Map<String, String> map) {
		if (isLeaf() && value != null) {
			map.put(getPath(), value);
		}
		for (MetadataNode child : children) {
			child.flattenInto(map);
		}
	}

	@Override
	public String toString() {
		if (value != null) {
			return name + " = " + value;
		}
		return name;
	}
}
