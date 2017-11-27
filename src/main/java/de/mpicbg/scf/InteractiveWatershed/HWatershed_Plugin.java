package de.mpicbg.scf.InteractiveWatershed;


/*
Author: Benoit Lombardot, Scientific Computing Facility, MPI-CBG, Dresden  

Copyright 2017 Max Planck Institute of Molecular Cell Biology and Genetics, Dresden, Germany

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following 
conditions are met:

1 - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2 - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.

3 - Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived 
from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/




/*
 * TODO:
 * 	- Set smarter value for seed dynamics and intensity threshold
 * 	  maybe it could be done with a Dynamic plugin
 *  - remove the limitation to 3D image
 */

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.LutLoader;
import ij.process.ImageConverter;
import ij.process.LUT;
import net.imagej.ImageJ;
import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import de.mpicbg.scf.InteractiveWatershed.HWatershedLabeling.Connectivity;


@Plugin(type = Op.class, name="H_Watershed", headless = true, label="H_Watershed", visible=true, menuPath = "SCF>Labeling>H_Watershed") // menu is not set, meaning the plugin won't appear in imagej's menu
public class HWatershed_Plugin extends AbstractOp  {

	
	// -- Parameters --
	@Parameter (type = ItemIO.INPUT)
	private	ImagePlus impIN;
	
	@Parameter (type = ItemIO.OUTPUT)
	private	ImagePlus impOUT;
	
	@Parameter( label="Seed dynamics", persist=false, required=false ) // with persist and required set to false the parameter become optional
	private Float hMin;
	
	@Parameter( label="Intensity threshold", persist=false, required=false ) // with persist and required set to false the parameter become optional
	private Float thresh;
	
	@Parameter( label="peak flooding (in %)", persist=false, required=false ) // with persist and required set to false the parameter become optional
	private Float peakFlooding = 100f;
	
	@Parameter(label = "export regions mask", persist = false, required=false , description="if checked the output will be compatible with the particle analyzer") // persist is important otherwise it keep the value used previously independant what is set manually
	private Boolean outputMask = false;
	
	FloatType min = new FloatType(Float.MAX_VALUE), max = new FloatType(Float.MIN_VALUE);
	Img<FloatType> imgIN;
	
	private void computeMinMax(){
		if( min.getRealFloat() > max.getRealFloat() )
			ComputeMinMax.computeMinMax(imgIN, min, max);
	}
	
	@Override
	public void run() {
		
		if (impIN == null){
			return;
		}
		
		imgIN = ImageJFunctions.convertFloat(impIN); 
		int nDims = imgIN.numDimensions();
		if( nDims>3){
			IJ.error("The Interactive Watershed plugin handles only graylevel 2D/3D images \n Current image has more dimensions." );
		}
		
		
		// Check parameters
		
		if ( hMin == null ){
			// if hMin is not provided set it to 5% of the image range
			computeMinMax();
			hMin =  0.05f * ( max.getRealFloat() - min.getRealFloat() ) ;
		}
		
		if ( thresh == null ){
			// if thresh is not provided, set it to the minimum of the image
			computeMinMax();
			thresh = min.getRealFloat();
		}
		
		
		
		// build the segment tree
		float threshold0 = Float.NEGATIVE_INFINITY; // we will flood the whole image in the first place
		HWatershedLabeling<FloatType> segmentTreeConstructor = new HWatershedLabeling<FloatType>(imgIN, threshold0 , Connectivity.FACE);
		Tree hSegmentTree = segmentTreeConstructor.getTree();
		
		// segment tree to label map  
		Img<IntType> hSegmentMap = segmentTreeConstructor.getLabelMapMaxTree();
		SegmentHierarchyToLabelMap<FloatType> segmentTreeLabeler = new SegmentHierarchyToLabelMap<FloatType>( hSegmentTree, hSegmentMap, imgIN );
		
		//boolean makeNewLabels = true;
		//segmentTreeLabeler.updateTreeLabeling( hMin , makeNewLabels);
		segmentTreeLabeler.updateTreeLabeling( hMin );
		Img<IntType> imgOUT = segmentTreeLabeler.getLabelMap( thresh , peakFlooding);
		

		
		// format the output image
		IntType minPixel = new IntType();
		IntType maxPixel = new IntType();
		ComputeMinMax<IntType> computeMinMax = new ComputeMinMax<>( imgOUT, minPixel, maxPixel);
		computeMinMax.process();
		
		if(  outputMask ) {
			imgOUT = Utils.getPARegions( imgOUT );
		}
		
		String title = impIN.getTitle() + " - watershed (h="+String.format("%5.2f", hMin)+", T="+String.format("%5.2f", thresh)+", %="+String.format("%2.0f", peakFlooding)+", n="+ maxPixel.get() +")";
		ImagePlus impOUT0 = ImageJFunctions.wrapFloat(imgOUT, title );
		impOUT = impOUT0.duplicate();
		impOUT.setTitle( title );
		int zMax=1;
		if( nDims==3 ) {
			zMax = (int)imgOUT.dimension(3); 
			impOUT.setDimensions(1, zMax, 1);
			impOUT.setOpenAsHyperStack(true);
		}
		
		
		
		if(outputMask) {
			boolean doScaling = ImageConverter.getDoScaling();
			ImageConverter.setDoScaling(false);
			IJ.run( impOUT , "8-bit", "");
			ImageConverter.setDoScaling( doScaling );
			impOUT.changes = false;
		}
		else {
			LUT segmentationLUT= null;
			try{
				segmentationLUT = LutLoader.openLut( IJ.getDirectory("luts") + File.separator + "glasbey_inverted.lut");
				impOUT.setLut(segmentationLUT);

			}finally{ /*do nothing*/ }
			if( segmentationLUT == null ){
				IJ.run(impOUT, "3-3-2 RGB", "");
			}
			impOUT.setDisplayRange(0,  maxPixel.get() , 0);
		}
		
		
	}
	
	
	public static <T extends RealType<T>> void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();//launch(args);
		
		// Launch the command .
		IJ.openImage("F:\\projects\\2DEmbryoSection_Mette.tif").show();
		//Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2DEmbryoSection_Mette.tif");
		//Dataset dataset2 = (Dataset) ij.io().open("F:\\projects\\2D_8peaks.tif");
		//ij.ui().show(dataset);
		
		ij.command().run(HWatershed_Plugin.class, true);
		
		
	}
	
	

}
