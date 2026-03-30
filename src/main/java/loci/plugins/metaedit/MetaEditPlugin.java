package loci.plugins.metaedit;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

import javax.swing.SwingUtilities;

/**
 * Fiji/ImageJ plugin entry point for MetaEdit.
 * Reads OME metadata from the active image and opens an editor dialog.
 *
 * Registered in plugins.config as:
 *   Plugins>LOCI, "MetaEdit - OME Metadata Editor", loci.plugins.metaedit.MetaEditPlugin
 */
public class MetaEditPlugin implements PlugIn {

	@Override
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.error("MetaEdit", "No image is open. Please open an image first.");
			return;
		}

		IJ.showStatus("MetaEdit: Reading metadata...");

		MetadataReader reader = new MetadataReader();
		MetadataNode root = reader.readMetadata(imp);

		if (root == null) {
			IJ.error("MetaEdit",
				"Could not read metadata from the current image.");
			return;
		}

		IJ.showStatus("MetaEdit: Opening editor...");

		SwingUtilities.invokeLater(() -> {
			MetaEditDialog dialog = new MetaEditDialog(imp, root);
			dialog.setVisible(true);
		});

		IJ.showStatus("");
	}
}
