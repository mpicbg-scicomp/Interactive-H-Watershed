
import de.mpicbg.scf.InteractiveWatershed.HWatershed_Plugin;
import ij.IJ;

/**
 * Author: HongKee Moon (moon@mpi-cbg.de), Scientific Computing Facility
 * Organization: MPI-CBG Dresden
 * Date: February 2025
 */
public class Main {
	public static void main(String[] args) {

		final net.imagej.ImageJ ij = new net.imagej.ImageJ();
		ij.ui().showUI();//launch(args);

		// Launch the command .
		IJ.openImage("http://imagej.net/images/FluorescentCells.zip").show();

		ij.command().run(HWatershed_Plugin.class, true);
	}
}
