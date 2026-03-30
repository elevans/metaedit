package loci.plugins.metaedit;

import ij.ImagePlus;
import ij.measure.Calibration;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Reads metadata from an already-open {@link ImagePlus} in Fiji.
 * <p>
 * Rather than re-reading the file with Bio-Formats, this class pulls
 * metadata from the stores that Fiji / Bio-Formats / SCIFIO already
 * populated when the image was opened:
 * <ul>
 *   <li>{@link ImagePlus#getProperties()} – key-value metadata from the reader</li>
 *   <li>{@link ImagePlus#getProperty(String) getProperty("Info")} – the serialized
 *       metadata string (shown by Image &gt; Show Info)</li>
 *   <li>{@link ImagePlus#getCalibration()} – pixel sizes, time interval, units</li>
 * </ul>
 */
public class MetadataReader {

	/**
	 * Build a metadata tree from the given ImagePlus.
	 *
	 * @param imp the currently open image
	 * @return root MetadataNode, or null if imp is null
	 */
	public MetadataNode readMetadata(ImagePlus imp) {
		if (imp == null) return null;

		MetadataNode root = new MetadataNode("Metadata");
		root.setEditable(false);

		root.addChild(buildImageInfoNode(imp));
		root.addChild(buildCalibrationNode(imp));

		MetadataNode propsNode = buildPropertiesNode(imp);
		if (!propsNode.getChildren().isEmpty()) {
			root.addChild(propsNode);
		}

		MetadataNode infoNode = buildInfoNode(imp);
		if (!infoNode.getChildren().isEmpty()) {
			root.addChild(infoNode);
		}

		return root;
	}

	// -- Image info from ImagePlus fields --

	private MetadataNode buildImageInfoNode(ImagePlus imp) {
		MetadataNode node = new MetadataNode("Image Info");
		node.setEditable(false);

		node.addChild(leaf("Title", imp.getTitle(), true));
		node.addChild(leaf("Width", String.valueOf(imp.getWidth()), false));
		node.addChild(leaf("Height", String.valueOf(imp.getHeight()), false));
		node.addChild(leaf("Slices (Z)", String.valueOf(imp.getNSlices()), false));
		node.addChild(leaf("Channels", String.valueOf(imp.getNChannels()), false));
		node.addChild(leaf("Frames (T)", String.valueOf(imp.getNFrames()), false));
		node.addChild(leaf("Bit Depth", String.valueOf(imp.getBitDepth()), false));
		node.addChild(leaf("Image Type", typeString(imp.getType()), false));

		if (imp.getOriginalFileInfo() != null) {
			String dir = imp.getOriginalFileInfo().directory;
			String name = imp.getOriginalFileInfo().fileName;
			if (dir != null && name != null) {
				node.addChild(leaf("File", dir + name, false));
			}
		}

		return node;
	}

	// -- Calibration (physical pixel sizes, units) --

	private MetadataNode buildCalibrationNode(ImagePlus imp) {
		MetadataNode node = new MetadataNode("Calibration");
		node.setEditable(false);

		Calibration cal = imp.getCalibration();
		node.addChild(leaf("Pixel Width", String.valueOf(cal.pixelWidth), true));
		node.addChild(leaf("Pixel Height", String.valueOf(cal.pixelHeight), true));
		node.addChild(leaf("Pixel Depth", String.valueOf(cal.pixelDepth), true));
		node.addChild(leaf("Spatial Unit", cal.getUnit(), true));
		node.addChild(leaf("Frame Interval", String.valueOf(cal.frameInterval), true));
		node.addChild(leaf("Time Unit", cal.getTimeUnit(), true));

		if (cal.xOrigin != 0 || cal.yOrigin != 0 || cal.zOrigin != 0) {
			node.addChild(leaf("X Origin", String.valueOf(cal.xOrigin), true));
			node.addChild(leaf("Y Origin", String.valueOf(cal.yOrigin), true));
			node.addChild(leaf("Z Origin", String.valueOf(cal.zOrigin), true));
		}

		return node;
	}

	// -- Properties stored directly on the ImagePlus --

	private MetadataNode buildPropertiesNode(ImagePlus imp) {
		MetadataNode node = new MetadataNode("Properties");
		node.setEditable(false);

		Properties props = imp.getProperties();
		if (props == null) return node;

		// Collect and sort properties (skip "Info" – handled separately)
		TreeMap<String, String> sorted = new TreeMap<>();
		for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
			String key = e.nextElement().toString();
			if ("Info".equals(key)) continue;
			Object val = props.get(key);
			if (val != null) {
				sorted.put(key, val.toString());
			}
		}

		for (Map.Entry<String, String> entry : sorted.entrySet()) {
			node.addChild(leaf(entry.getKey(), entry.getValue(), true));
		}

		return node;
	}

	// -- "Info" string (the serialized metadata from Bio-Formats / SCIFIO) --

	private MetadataNode buildInfoNode(ImagePlus imp) {
		MetadataNode node = new MetadataNode("Reader Metadata");
		node.setEditable(false);

		Object infoObj = imp.getProperty("Info");
		if (!(infoObj instanceof String)) return node;

		String info = (String) infoObj;
		if (info.isEmpty()) return node;

		// Bio-Formats / SCIFIO writes "key = value" or "key: value" lines.
		// Group by common prefix to create a navigable hierarchy.
		Map<String, MetadataNode> groups = new LinkedHashMap<>();

		for (String line : info.split("\\r?\\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;

			String key;
			String value;
			int eqIdx = line.indexOf(" = ");
			if (eqIdx > 0) {
				key = line.substring(0, eqIdx).trim();
				value = line.substring(eqIdx + 3).trim();
			} else {
				int colonIdx = line.indexOf(": ");
				if (colonIdx > 0) {
					key = line.substring(0, colonIdx).trim();
					value = line.substring(colonIdx + 2).trim();
				} else {
					key = line;
					value = "";
				}
			}

			String groupName = extractGroupName(key);
			MetadataNode group = groups.computeIfAbsent(groupName, name -> {
				MetadataNode g = new MetadataNode(name);
				g.setEditable(false);
				return g;
			});

			String leafName = stripGroupPrefix(key, groupName);
			group.addChild(leaf(leafName, value, true));
		}

		for (MetadataNode group : groups.values()) {
			if (group.getChildren().size() == 1 &&
				group.getName().equals(group.getChildren().get(0).getName())) {
				MetadataNode child = group.getChildren().get(0);
				node.addChild(leaf(child.getName(), child.getValue(), child.isEditable()));
			} else {
				node.addChild(group);
			}
		}

		return node;
	}

	/**
	 * Extract a group name from a metadata key.
	 * "Global SomeKey" → "Global";
	 * "Series 0 SomeKey" → "Series 0";
	 * "Namespace.Field" → "Namespace";
	 * plain key → "General".
	 */
	private String extractGroupName(String key) {
		if (key.startsWith("Global ")) return "Global";
		if (key.startsWith("Series ")) {
			int secondSpace = key.indexOf(' ', 7);
			if (secondSpace > 0) return key.substring(0, secondSpace);
			return key;
		}
		int dotIdx = key.indexOf('.');
		if (dotIdx > 0 && dotIdx < key.length() - 1) {
			return key.substring(0, dotIdx);
		}
		return "General";
	}

	private String stripGroupPrefix(String key, String groupName) {
		if ("General".equals(groupName)) return key;
		if (key.startsWith(groupName)) {
			String rest = key.substring(groupName.length()).trim();
			if (!rest.isEmpty() && (rest.charAt(0) == '.' ||
				rest.charAt(0) == '|' || rest.charAt(0) == ' ')) {
				rest = rest.substring(1).trim();
			}
			return rest.isEmpty() ? key : rest;
		}
		return key;
	}

	// -- Helpers --

	private MetadataNode leaf(String name, String value, boolean editable) {
		MetadataNode n = new MetadataNode(name, value != null ? value : "");
		n.setEditable(editable);
		return n;
	}

	private String typeString(int type) {
		switch (type) {
			case ImagePlus.GRAY8: return "8-bit";
			case ImagePlus.GRAY16: return "16-bit";
			case ImagePlus.GRAY32: return "32-bit";
			case ImagePlus.COLOR_256: return "8-bit color";
			case ImagePlus.COLOR_RGB: return "RGB";
			default: return "Unknown";
		}
	}
}
