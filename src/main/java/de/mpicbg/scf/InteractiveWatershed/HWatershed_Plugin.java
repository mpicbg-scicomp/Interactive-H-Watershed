package de.mpicbg.scf.InteractiveWatershed;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.LutLoader;
import ij.process.LUT;
import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpicbg.scf.InteractiveWatershed.HWatershedLabeling.Connectivity;


@Plugin(type = Op.class, name="H-Watershed", menuPath = "SCF>Labeling>H-Watershed", headless = true, label="H-Watershed", visible=true)
public class HWatershed_Plugin extends AbstractOp  {

	
	
	// -- Parameters --
	@Parameter (type = ItemIO.INPUT)
	private	ImagePlus impIN;
	
	@Parameter (type = ItemIO.OUTPUT)
	private	ImagePlus impOUT;
	
	@Parameter( label="Seed dynamics", stepSize="0.05")
	private Float hMin;
	
	@Parameter( label="Intensity threshold")
	private Float thresh;
	
	@Parameter( label="peak flooding (in %)")
	private Float peakFlooding;
	
	
	@Override
	public void run() {
		
		if (impIN == null){
			return;
		}
		
		Img<FloatType> imgIN = ImageJFunctions.convertFloat(impIN); 
		int nDims = imgIN.numDimensions();
		if( nDims>3){
			IJ.error("The Interactive Watershed plugin handles only graylevel 2D/3D images \n Current image has more dimensions." );
		}
		
		// build the segment tree
		HWatershedLabeling<FloatType> segmentTreeConstructor = new HWatershedLabeling<FloatType>(imgIN, thresh, Connectivity.FACE);
		Tree hSegmentTree = segmentTreeConstructor.getTree();
		
		// segment tree to label map  
		Img<IntType> hSegmentMap = segmentTreeConstructor.getLabelMapMaxTree();
		SegmentHierarchyToLabelMap<FloatType> segmentTreeLabeler = new SegmentHierarchyToLabelMap<FloatType>( hSegmentTree, hSegmentMap, imgIN );
		
		boolean makeNewLabels = true;
		int nLabels = segmentTreeLabeler.updateTreeLabeling( hMin , makeNewLabels);
		Img<IntType> imgOUT = segmentTreeLabeler.getLabelMap( thresh , peakFlooding);
		
		// format the output image
		impOUT = ImageJFunctions.wrapFloat(imgOUT, impIN.getTitle() + " - watershed (h="+String.format("%5.2f", hMin)+", T="+String.format("%5.2f", thresh)+", %="+String.format("%2.0f", peakFlooding)+", n="+nLabels+")" );
		int zMax=1;
		if( nDims==3 )
			zMax = (int)imgOUT.dimension(3); 
		
		impOUT.setDimensions(1, zMax, 1);
		impOUT.setOpenAsHyperStack(true);
		LUT segmentationLUT= null;
		try{
			segmentationLUT = LutLoader.openLut( IJ.getDirectory("luts") + File.separator + "glasbey_inverted.lut");
			impOUT.setLut(segmentationLUT);

		}finally{ /*do nothing*/ }
		if( segmentationLUT == null ){
			IJ.run(impOUT, "3-3-2 RGB", "");
		}
		impOUT.setDisplayRange(0, nLabels, 0);
		
		
		
	}

}
