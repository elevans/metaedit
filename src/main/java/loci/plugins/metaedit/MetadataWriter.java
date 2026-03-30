package loci.plugins.metaedit;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Applies edited metadata back to the {@link ImagePlus} in memory and
 * optionally saves a companion metadata XML file for persistence.
 */
public class MetadataWriter {

	/**
	 * Apply edits from the metadata tree back to the ImagePlus,
	 * then write a companion XML file next to the original image.
	 *
	 * @param imp  the currently open image
	 * @param root the root MetadataNode with edits
	 * @return the companion file path, or null if no file could be written
	 */
	public String save(ImagePlus imp, MetadataNode root) {
		// Apply edits to the live ImagePlus
		applyToImagePlus(imp, root);

		// Persist to companion file if a file path is available
		String filePath = getFilePath(imp);
		if (filePath != null) {
			String companionPath = deriveCompanionPath(filePath);
			if (writeCompanionXml(companionPath, root)) {
				return companionPath;
			}
		}
		return null;
	}

	// -- Apply edits to the live ImagePlus --

	private void applyToImagePlus(ImagePlus imp, MetadataNode root) {
		List<MetadataNode> modified = root.getModifiedNodes();
		for (MetadataNode node : modified) {
			String path = node.getPath();
			String value = node.getValue();

			try {
				if (path.startsWith("Metadata/Image Info/")) {
					applyImageInfoEdit(imp, node.getName(), value);
				}
				else if (path.startsWith("Metadata/Calibration/")) {
					applyCalibrationEdit(imp, node.getName(), value);
				}
				else if (path.startsWith("Metadata/Properties/")) {
					imp.setProperty(node.getName(), value);
				}
			}
			catch (Exception e) {
				IJ.log("MetaEdit: Could not apply edit for " +
					path + ": " + e.getMessage());
			}
		}

		// Ensure the display refreshes
		imp.updateAndRepaintWindow();
	}

	private void applyImageInfoEdit(ImagePlus imp, String field, String value) {
		if ("Title".equals(field)) {
			imp.setTitle(value);
		}
	}

	private void applyCalibrationEdit(ImagePlus imp, String field, String value) {
		Calibration cal = imp.getCalibration();
		switch (field) {
			case "Pixel Width":
				cal.pixelWidth = Double.parseDouble(value);
				break;
			case "Pixel Height":
				cal.pixelHeight = Double.parseDouble(value);
				break;
			case "Pixel Depth":
				cal.pixelDepth = Double.parseDouble(value);
				break;
			case "Spatial Unit":
				cal.setUnit(value);
				break;
			case "Frame Interval":
				cal.frameInterval = Double.parseDouble(value);
				break;
			case "Time Unit":
				cal.setTimeUnit(value);
				break;
			case "X Origin":
				cal.xOrigin = Double.parseDouble(value);
				break;
			case "Y Origin":
				cal.yOrigin = Double.parseDouble(value);
				break;
			case "Z Origin":
				cal.zOrigin = Double.parseDouble(value);
				break;
		}
	}

	// -- Companion XML persistence --

	private boolean writeCompanionXml(String path, MetadataNode root) {
		try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
			out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			out.println("<MetaEdit>");
			writeNodeXml(out, root, 1);
			out.println("</MetaEdit>");
			IJ.log("MetaEdit: Saved companion metadata to " + path);
			return true;
		}
		catch (IOException e) {
			IJ.log("MetaEdit: I/O error saving companion XML: " + e.getMessage());
			return false;
		}
	}

	private void writeNodeXml(PrintWriter out, MetadataNode node, int depth) {
		String indent = "  ".repeat(depth);
		String safeName = xmlEscape(node.getName());

		if (node.getChildren().isEmpty()) {
			// Leaf node
			String safeValue = xmlEscape(node.getValue());
			out.println(indent + "<Entry key=\"" + safeName +
				"\" value=\"" + safeValue +
				"\" modified=\"" + node.isModified() + "\"/>");
		}
		else {
			// Group node
			out.println(indent + "<Group name=\"" + safeName + "\">");
			for (MetadataNode child : node.getChildren()) {
				writeNodeXml(out, child, depth + 1);
			}
			out.println(indent + "</Group>");
		}
	}

	private String xmlEscape(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
	}

	private String deriveCompanionPath(String originalPath) {
		int dotIndex = originalPath.lastIndexOf('.');
		String basePath = dotIndex > 0
			? originalPath.substring(0, dotIndex)
			: originalPath;
		return basePath + ".companion.metaedit.xml";
	}

	private String getFilePath(ImagePlus imp) {
		if (imp == null || imp.getOriginalFileInfo() == null) return null;
		String dir = imp.getOriginalFileInfo().directory;
		String name = imp.getOriginalFileInfo().fileName;
		if (dir != null && name != null) {
			if (!dir.endsWith("/") && !dir.endsWith("\\")) {
				dir = dir + "/";
			}
			return dir + name;
		}
		return null;
	}
}
