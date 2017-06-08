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


import java.awt.Component;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.ScrollbarWithLabel;
import ij.measure.Calibration;
import ij.plugin.LutLoader;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.plugin.frame.Recorder;

import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.command.Previewable;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;
import org.scijava.module.MutableModuleItem;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;

import de.mpicbg.scf.InteractiveWatershed.HWatershedLabeling.Connectivity;


/**
 * An plugin allowing to explore the watershed from hMaxima 
 * 
 * known issue: the input harvester cannot be refreshed : view image list is not updated
 * 
 */
@Plugin(type = Command.class, menuPath = "SCF>Labeling>Interactive H_Watershed", initializer="initialize_HWatershed", headless = true, label="Interactive H_Watershed")
public class Interactive_HWatershed extends InteractiveCommand implements Previewable, MouseMotionListener {

	// -- Parameters --
	@Parameter (type = ItemIO.BOTH)
	private	ImagePlus imp0;
	
	@Parameter(label = "Analyzed image" , visibility = ItemVisibility.MESSAGE, persist = false)
	private String analyzedImageName = "test";
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Seed dynamics", stepSize="0.05")
	private Float hMin_log;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Intensity threshold", stepSize="0.05")
	private Float thresh_log;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="peak flooding (in %)", min="0", max="100")
	private Float peakFlooding;
	
	@Parameter(style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Image", "Contour overlay", "Solid overlay" } )
	private String displayStyle = "Image";
	
	@Parameter(label = "Slicing axis", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "X", "Y", "Z" }, persist = false)
	private String slicingDirection = "Z";
	
	@Parameter(label = "View image", style = ChoiceWidget.LIST_BOX_STYLE, persist = false) // persist is important otherwise it keep the value used previously independant what is set manually
	private String imageToDisplayName;
	
	@Parameter(label = "export", callback="exportButton_callback" )
	private Button exportButton;
	
	
	//@Parameter
	//private EventService eventService;
	
	
	int[] pos= new int[] { 1, 1, 1};
	double[] spacing = new double[] { 1, 1, 1};
	int displayOrient = -1; // 2 is for display orient perpendicular to z direction
	LUT segLut;
	LUT imageLut;
	double[] segDispRange = new double[2];
	double[] imageDispRange = new double[2];
	
	// to keep track of UI change
	String displayStyleOld;			//
	String imageToDisplayOldName;	//
	Map<String, Boolean> changed;	//
	Map<String, Double> previous;	//
	
	float minI, maxI; 				// min and max intensity of the input image
	int nDims; 						// dimensionnality of the input image
	SegmentHierarchyToLabelMap<FloatType> segmentTreeLabeler;

	ImagePlus impSegmentationDisplay; // the result window interactively updated
	ImagePlus imp_curSeg; // container of the current labelMap slice
	
	ImageListener impListener;
	
	
	
	
	
	
	boolean upSample = true;
	
	boolean initDone = false;
	
	boolean readyToFire = true;
	boolean needUpdate = true;
	
	boolean initInterupted = false;
	
	double[] displaySpacing;
	
	// -- Command methods --

	@Override
	public void run() {  
		// not implemented
		// on export a string is printed allowing to reproduce the same result with a simplified plugin
	}


	// -- Initializer methods --
	protected void initialize_HWatershed() {	
		if( initDone ){ return; }
		
		if (imp0 == null){
			return;
		}
		
		Img<FloatType> input = ImageJFunctions.convertFloat(imp0); 
		nDims = input.numDimensions();
		if( nDims>3){
			IJ.error("The Interactive Watershed plugin handles only graylevel 2D/3D images \n Current image has more dimensions." );
		}
		
		
		imageToDisplayName = imp0.getTitle();
		imageToDisplayOldName = imageToDisplayName;
		displayStyleOld = displayStyle;
		Calibration cal = imp0.getCalibration();
		spacing = new double[] {cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
		displaySpacing = new double[] {spacing[0], spacing[1]};
		
		///////////////////////////////////////////////////////////////////////////
		// create the HSegmentTree ////////////////////////////////////////////////
		
		float threshold = Float.NEGATIVE_INFINITY;
		HWatershedLabeling<FloatType> segmentTreeConstructor = new HWatershedLabeling<FloatType>(input, threshold, Connectivity.FACE);
		Tree hSegmentTree = segmentTreeConstructor.getTree();
		Img<IntType> hSegmentMap = segmentTreeConstructor.getLabelMapMaxTree();
		if ( hSegmentMap==null ){
			initInterupted=true;
			IJ.error("H-Watershed construction was manually interupted, please close the interactive watershed dialog.");
			
			// initialize analyzed image name  ////////////////////// 
			analyzedImageName = "Initialisation was interupted, please close the plugin.";
			final MutableModuleItem<String> AnalyzedImageItem = getInfo().getMutableInput("analyzedImageName", String.class);
			AnalyzedImageItem.setValue(this, analyzedImageName );
			
			return;
		}
		segmentTreeLabeler = new SegmentHierarchyToLabelMap<FloatType>( hSegmentTree, hSegmentMap, input );
		
		
		

		///////////////////////////////////////////////////////////////////////////
		// Initialize the UI //////////////////////////////////////////////////////

		double[] dynamics = hSegmentTree.getFeature("dynamics");
		maxI = (float) Arrays.stream(dynamics).max().getAsDouble();
		minI = (float) Arrays.stream(dynamics).min().getAsDouble(); ;
		
		
		// initialize analyzed image name  ////////////////////// 
		final MutableModuleItem<String> AnalyzedImageItem = getInfo().getMutableInput("analyzedImageName", String.class);
		AnalyzedImageItem.setValue(this, imp0.getTitle() );
		
		// initialize seed threshold (jMin) slider attributes ////////////////////// 
		final MutableModuleItem<Float> thresholdItem = getInfo().getMutableInput("hMin_log", Float.class);
		thresholdItem.setMinimumValue( new Float(0) );
		thresholdItem.setMaximumValue( new Float(Math.log(maxI-minI+1) * 100.0 ));
		thresholdItem.setStepSize( 0.05);
		hMin_log = 0f;
		thresholdItem.setValue(this, hMin_log);
		
		// initialize intensity threshold slider attributes ////////////////////////////
		final MutableModuleItem<Float> thresholdItem2 = getInfo().getMutableInput("thresh_log", Float.class);
		thresholdItem2.setMinimumValue( new Float(0) );
		thresholdItem2.setMaximumValue( new Float(Math.log(maxI-minI+1) * 100.0 ));
		thresholdItem2.setStepSize( 0.05);
		thresh_log = 0f;
		thresholdItem2.setValue(this, thresh_log);
		
		// initialize peak flooding (%) slider attributes ////////////////////////////
		final MutableModuleItem<Float> thresholdItem3 = getInfo().getMutableInput("peakFlooding", Float.class);
		peakFlooding = 100f;
		thresholdItem3.setValue(this, peakFlooding);
		
				
		// initialize the image List attributes ////////////////////////////
		updateImagesToDisplay();
		
		// slicing direction
		if( nDims==2){
			System.out.println("test, slicingDirection "+ slicingDirection);
			final MutableModuleItem<String> slicingItem = getInfo().getMutableInput("slicingDirection", String.class);
			System.out.println("test, slicingDirection "+ slicingDirection);
			getInfo().removeInput( slicingItem );
		}
		
		
		///////////////////////////////////////////////////////////////////////////////////
		// initialize plugin state ////////////////////////////////////////////////////////
		
		// collect information to initialize visualization of the segmentation display
		imageDispRange[0] = (float)imp0.getDisplayRangeMin();
		imageDispRange[1] = (float)imp0.getDisplayRangeMax();
		
		int nLabel = hSegmentTree.getNumNodes();
		segDispRange[0] = 0;
		segDispRange[1] = 2*nLabel;
		
		segLut = LutLoader.openLut( IJ.getDirectory("luts") + File.separator + "glasbey_inverted.lut");
		imageLut = (LUT) imp0.getProcessor().getLut().clone();
		
		
		// initialize UI status
		changed = new HashMap<String,Boolean>();
		changed.put("pos", 				false);
		changed.put("hMin", 			false);
		changed.put("thresh", 			false);
		changed.put("peakFlooding", 	false);
		changed.put("displayOrient",	false);
		//System.out.println(displayOrientString + " : "+ displayOrient);
		
		//displayOrient = getDisplayOrient();
		previous = new HashMap<String,Double>();
		previous.put("pos", 			(double)1);
		previous.put("hMin", 			(double)getHMin());
		previous.put("thresh", 			(double)getThresh());
		previous.put("peakFlooding", 	(double)peakFlooding);
		
		
		
		
		segmentTreeLabeler.updateTreeLabeling( getHMin() );
		Img<IntType> img_currentSegmentation = segmentTreeLabeler.getLabelMap(getThresh(), peakFlooding, 2, 0);
		imp_curSeg = ImageJFunctions.wrapFloat(img_currentSegmentation, "treeCut");
		
		// create the window to show the segmentation display
		//updateSegmentationDisplay();
		//render();		
		//preview();
		
		
		initDone = true;
	} // end of the initialization! 
	
	
	private float getHMin(){
		return (float)Math.exp(hMin_log / 100.0)+minI-1;
	}
	
	
	
	private float getThresh(){
		return (float)Math.exp(thresh_log / 100.0)+minI-1;
	}
	
	
	
	private int getDisplayOrient(){
		
		int displayOrient;
		
		if( slicingDirection.equals("Y") ){
			displayOrient=1;
		}
		else if( slicingDirection.equals("X") ){
			displayOrient=0;
		}
		else{ //if( slicingDirection.equals("Z") ){
			displayOrient=2;
		}
		return displayOrient;
	}
	
	
	
	private void updateImagesToDisplay(){
		
		//System.out.println("imageToDisplayName " + imageToDisplayName );
		
		
		List<String> nameList = new ArrayList<String>();
		String[] imageNames = WindowManager.getImageTitles();
		
		//System.out.println(ArrayUtils.toString(imageNames) );
		
		
		int[] dims0 = imp0.getDimensions();
		for(String imageName : imageNames){
			ImagePlus impAux = WindowManager.getImage(imageName);
			
			int[] dims = impAux.getDimensions();
			
			//System.out.println(imageName+" : "+impAux.getTitle() );
			//System.out.println(ArrayUtils.toString(dims) );
			
			boolean isSizeEqual = true;
			for(int d=0; d<dims.length; d++)
				if( dims0[d]!=dims[d] )
					isSizeEqual = false;
			if( isSizeEqual ){
				nameList.add(imageName);
			}
		}
		nameList.add("None");
		//nameList.add("Z");
		//nameList.add("0");
		
		
		if( impSegmentationDisplay != null){
			if ( nameList.contains( impSegmentationDisplay.getTitle() ) ){
				nameList.remove( impSegmentationDisplay.getTitle() );
			}
		}
		int idx;
		if( !nameList.contains(imageToDisplayName) ){
			idx = nameList.indexOf("None");
		}
		else
			idx = nameList.indexOf(imageToDisplayName); 
			
		final MutableModuleItem<String> imageToDisplayItem = getInfo().getMutableInput("imageToDisplayName", String.class);
		imageToDisplayItem.setChoices( nameList );
		
		//System.out.println("imageToDisplayName " + imageToDisplayName );
		imageToDisplayItem.setValue(this, nameList.get(idx) );
		
		//System.out.println("itemValue " + imageToDisplayItem.getValue(this) );
		//System.out.println("imageToDisplayName " + imageToDisplayName );
		
		//this.update( imageToDisplayItem , imageToDisplayName );
		//this.updateInput( imageToDisplayItem );
		
	}
	
	
	
	@Override
	public void preview(){
		
		// check which parameter changed and update necessary value
		if( initInterupted ){
			return;
		}
		if( !wasStateChanged() ){
			return;
		}
		else{
			needUpdate=true;
		}
		
		
		if( !readyToFire){
			if( needUpdate ){ // wait 200 millisecond and try to preview again
				try {
					TimeUnit.MILLISECONDS.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				preview();
			}
			return;
		}
		readyToFire = false;
		
		
		
		
		
		// update labelMap slice to visualize
		if( changed.get("thresh") || changed.get("pos") || changed.get("peakFlooding") || changed.get("displayOrient"))
		{
			Img<IntType> img_currentSegmentation = segmentTreeLabeler.getLabelMap(getThresh(), peakFlooding, displayOrient, pos[displayOrient]-1);
			RandomAccessibleInterval<IntType> rai_currentSegmentation =  Views.dropSingletonDimensions(img_currentSegmentation);
			
			
			long[] dimTest = new long[img_currentSegmentation.numDimensions()];
			img_currentSegmentation.dimensions(dimTest);
			//System.out.println("img_currentSegmentation "+ArrayUtils.toString( dimTest ) );
			//System.out.println("slicing direction "+ displayOrient);
			//System.out.println("pos[slicingDir] "+ pos[displayOrient]);
			
			imp_curSeg = ImageJFunctions.wrapFloat(rai_currentSegmentation, "treeCut");
		}
		else if( changed.get("hMin") )
		{
			boolean makeNewLabels = true ; 
			int nLabels = segmentTreeLabeler.updateTreeLabeling( getHMin() , makeNewLabels );
			segDispRange[0] = 0;
			segDispRange[1] = nLabels;
			Img<IntType> img_currentSegmentation = segmentTreeLabeler.getLabelMap(getThresh(), peakFlooding, displayOrient, pos[displayOrient]-1);
			RandomAccessibleInterval<IntType> rai_currentSegmentation =  Views.dropSingletonDimensions(img_currentSegmentation);
			
			imp_curSeg = ImageJFunctions.wrapFloat(rai_currentSegmentation, "treeCut");
		}
		
		
		//int[] dimsTest = imp_curSeg.getDimensions();
		//System.out.println("imp_curSeg "+ArrayUtils.toString( dimsTest ) );
		//System.out.println("slicing direction "+ displayOrient);
		//System.out.println("pos[slicingDir] "+ pos[displayOrient]);
		
		render();	
		
		readyToFire = true;
		needUpdate = false;
	}
	
	
	
	private boolean wasStateChanged(){
		
		// update the saved display parameter in case they were changed
		/*
		System.out.println( impSegmentationDisplay.toString() );
		if( !impSegmentationDisplay.isVisible()){
			impSegmentationDisplay.show();
			System.out.println( "impSegmentationDisplay is not visible" );
		}
		if( impSegmentationDisplay.getWindow() == null){
			System.out.println( "impSegmentationDisplay window is null" );
			impSegmentationDisplay.flush();
			impSegmentationDisplay=null;
		}
		else{
			System.out.println("impSegmentationDisplay window :" + impSegmentationDisplay.getWindow().toString() );
		}*/
		
		if( impSegmentationDisplay!=null ){
			if (displayStyleOld.startsWith("Contour") | displayStyleOld.startsWith("Solid") ){
				imageDispRange[0] = impSegmentationDisplay.getDisplayRangeMin();
				imageDispRange[1] = impSegmentationDisplay.getDisplayRangeMax();
				imageLut = (LUT) impSegmentationDisplay.getProcessor().getLut().clone();
				
				System.out.println("image LUT: " + imageLut.toString() + "  ;  range: " + Arrays.toString(imageDispRange) );
			}
			else{
				segDispRange[0] = impSegmentationDisplay.getDisplayRangeMin();
				segDispRange[1] = impSegmentationDisplay.getDisplayRangeMax();
				segLut = (LUT) impSegmentationDisplay.getProcessor().getLut().clone();
				
				System.out.println("update seg LUT: " + segLut.toString() + "  ;  range: " + Arrays.toString(segDispRange) );
			}
		}
		else{
			//System.out.println( "impSegmentationDisplay==null" );
			//updateSegmentationDisplay();
		}
		
		//update the list of image that can be overlaid by the segmentation
		//updateImagesToDisplay();
		
		// reset all changed field to false
		for(String key : changed.keySet() ){
			changed.put(key, false);
		}
		
		// test which parameter has changed (only one can change at a time rk: not true if long update :-\ )
		boolean wasChanged  = false;
		//System.out.println("getTresh():"+getThresh()+"  ;  previous thresh"+previous.get("thresh"));
		if( getThresh() != previous.get("thresh") ){
			changed.put("thresh",true);
			previous.put( "thresh" , (double)getThresh() );
			//System.out.println("updated  :    getTresh():"+getThresh()+"  ;  previous thresh"+previous.get("thresh"));
			
			wasChanged  = true;
		}
		else if( getHMin() != previous.get("hMin") ){
			changed.put("hMin",true);
			previous.put( "hMin" , (double)getHMin() );
			wasChanged  = true;
		}
		else if( (double)peakFlooding != previous.get("peakFlooding") ){
			changed.put("peakFlooding",true);
			previous.put( "peakFlooding" , (double)peakFlooding );
			wasChanged  = true;
		}
		else if( displayOrient != getDisplayOrient() ){
			displayOrient = getDisplayOrient();
			changed.put("displayOrient",true);
			updateSegmentationDisplay();
			wasChanged  = true;
		}
		else if( (double)pos[displayOrient] != previous.get("pos") ){
			changed.put("pos",true);
			previous.put( "pos" , (double)pos[displayOrient] );
			wasChanged  = true;
		}
		else if( displayStyleOld != displayStyle ){
			displayStyleOld = displayStyle;
			wasChanged  = true;
		}
		else if( ! imageToDisplayOldName.equals(imageToDisplayName) ){
			imageToDisplayOldName = imageToDisplayName;
			wasChanged  = true;
		}
		
		//System.out.println( previous.toString() );
		//System.out.println( changed.toString() );
		
		
		return wasChanged;
	}
	
	
	
	private void updateSegmentationDisplay(){
		
		int[] dims=imp0.getDimensions();
		
		if( nDims==2 ){
			if( impSegmentationDisplay == null ){
				impSegmentationDisplay = IJ.createImage("interactive watershed", (int)imp0.getWidth(), (int)imp0.getHeight(), 1, 32);
			}
		}
		else{
			int[] dimensions = new int[nDims-1];
			
			int count=0;
			int[] dIndex = new int[] {0,1,3};
			for(int d=0; d<nDims; d++){
				if(d != displayOrient){
					dimensions[count]= dims[ dIndex[d] ];
					displaySpacing[count] = spacing[d];
					count++;
				}
			}
			if( impSegmentationDisplay != null ){
				impSegmentationDisplay.hide();
				impSegmentationDisplay.flush();
			}
			
			if( upSample ){
				double minDispSpacing = Math.min( displaySpacing[0], displaySpacing[1] );
				float[] upSampleFactors = new float[] {(float) (displaySpacing[0]/minDispSpacing) , (float) (displaySpacing[1]/minDispSpacing)};
				dimensions[0] = (int) (dimensions[0]*upSampleFactors[0]); 
				dimensions[1] = (int) (dimensions[1]*upSampleFactors[1]); 
			}
				
			impSegmentationDisplay = IJ.createImage("interactive watershed-"+slicingDirection, "32-bit", (int)dimensions[0], (int)dimensions[1], 1, (int)dims[dIndex[displayOrient]], 1);
			
		}
		
		impSegmentationDisplay.setZ(pos[displayOrient]);
		impSegmentationDisplay.show();
		addListenerToSegDisplay();
		
		
	}
	
	
	
	private void addListenerToSegDisplay(){
		
		// ready to use component listener on the slider of impSegmentationDisplay 		
 		Component[] components = impSegmentationDisplay.getWindow().getComponents();
		for(Component comp : components){
			if( comp instanceof ScrollbarWithLabel){ 				
				ScrollbarWithLabel scrollBar = (ScrollbarWithLabel) comp;
				Component[] components2 =  scrollBar.getComponents();
				for(Component comp2 : components2){
					if( comp2 instanceof Scrollbar){
						Scrollbar scrollBar2 = (Scrollbar) comp2;
						scrollBar2.addAdjustmentListener( new AdjustmentListener(){
							@Override
							public void adjustmentValueChanged(AdjustmentEvent e) {
								pos[displayOrient] = impSegmentationDisplay.getSlice();
								preview();
							}
						});
					}
				}
			}
		}
  				
		
		
		impListener = new ImageListener(){

			@Override
			public void imageClosed(ImagePlus imp) {
				
				//ImagePlus.removeImageListener(this);
				//if( imp.equals(impSegmentationDisplay)){
				//	impSegmentationDisplay.flush();
				//	impSegmentationDisplay = null;
				//	updateSegmentationDisplay();
				//	render();
				//}
				
			}

			@Override
			public void imageOpened(ImagePlus arg0) {	}

			@Override
			public void imageUpdated(ImagePlus arg0) {
				ImagePlus.removeImageListener(this);

				pos[displayOrient] = impSegmentationDisplay.getSlice();
				if ( pos[displayOrient] != previous.get("pos") )
					preview();
				
				ImagePlus.addImageListener(this);
			}	
		};
		
		ImagePlus.addImageListener(impListener); // not always satisfying  does not always register the action
		
		
		impSegmentationDisplay.getCanvas().addMouseMotionListener( this );
		
		/*
		KeyListener keyListener = new KeyListener(){
			@Override
			public void keyPressed(KeyEvent e) {
				pos[displayOrient] = impSegmentationDisplay.getSlice();
				preview();	
				System.out.println("hello key pressed");
				e.consume();
			}
			@Override
			public void keyTyped(KeyEvent e) { System.out.println("hello key typed");}
			@Override
			public void keyReleased(KeyEvent e) { System.out.println("hello key released");}
		};
		*/
		
		//impSegmentationDisplay.getCanvas().addKeyListener( keyListener );
		//impSegmentationDisplay.getWindow().addKeyListener( keyListener );
				
		
	}
	
	
	
	protected <T extends NumericType<T>> void render(){
			
		//get the processor of the segmentation to be displayed
		ImagePlus imp_curSeg2;
		if( upSample ){	
			Img<FloatType> imgCurSeg = ImageJFunctions.wrapFloat( imp_curSeg );
			long[] outSize = new long[] {impSegmentationDisplay.getWidth(), impSegmentationDisplay.getHeight()};
			Img<FloatType> imgCurSeg2 = Utils.upsample( imgCurSeg, outSize, Utils.Interpolator.NearestNeighbor);
			imp_curSeg2 = ImageJFunctions.wrap(imgCurSeg2, "segmentation");
		}
		else{
			imp_curSeg2 = imp_curSeg;
		}
		imp_curSeg.getProcessor().setLut(segLut);
		imp_curSeg2.setDisplayRange( segDispRange[0], segDispRange[1]); 
		ImageProcessor ip_curSeg  = imp_curSeg2.getProcessor();
		ip_curSeg.setLut( segLut );
		
		
		
		// get the processor of the background image to be displayed
		ImageProcessor input_ip;
		if( imageToDisplayName.equals("None") || imageToDisplayName.equals("Z") || imageToDisplayName.equals("0")){
			input_ip = new FloatProcessor( impSegmentationDisplay.getWidth(), impSegmentationDisplay.getHeight() );
		}
		else{
			ImagePlus impToDisplay = WindowManager.getImage( imageToDisplayName );
				
			ImagePlus impToDisplaySlice;
			if( nDims == 2){
				impToDisplaySlice = impToDisplay;
			}
			else{
				Img<? extends RealType<?> > imgToDisplay = Utils.wrapImagePlus( impToDisplay );
				
				
				long[] dimDisplay = new long[ imgToDisplay.numDimensions()];
				imgToDisplay.dimensions(dimDisplay);
					
				RandomAccessibleInterval<? extends RealType<?> > slice =  Views.hyperSlice(imgToDisplay, displayOrient, pos[displayOrient]-1);
				slice =  Views.dropSingletonDimensions(slice);
							
				int sNDim0 = slice.numDimensions();
				long[] sDim0 = new long[sNDim0];
				slice.dimensions(sDim0);
				//System.out.println("slice dim: "+ArrayUtils.toString(sDim0));
					
				Cursor<? extends RealType<?> > cSlice0 =  (Cursor<? extends RealType<?>>)  Views.flatIterable(slice).cursor();
				
				//Img< FloatType > imgSlice = imgToDisplay.factory().create( new long[] { sDim0[0], sDim0[1] }, new FloatType(0) );
				
				Img<FloatType> imgSlice = new ArrayImgFactory< FloatType >().create( new long[] { sDim0[0], sDim0[1] }, new FloatType() );
				Cursor< FloatType > cSlice = imgSlice.cursor();
				while(cSlice.hasNext())
					cSlice.next().setReal(cSlice0.next().getRealFloat() );
						
				Img< FloatType > imgSlice2;
				if( upSample ){	
					long[] outSize = new long[] {impSegmentationDisplay.getWidth(), impSegmentationDisplay.getHeight()};
					imgSlice2 = Utils.upsample(imgSlice, outSize, Utils.Interpolator.NearestNeighbor);	
				}
				else{
					imgSlice2 = imgSlice;
				}
				impToDisplaySlice = ImageJFunctions.wrap(imgSlice2, "test");
			}			
			input_ip = impToDisplaySlice.getProcessor().convertToFloat();
		}
		
		
		//System.out.println("segLut: "+segLut.toString() );
		
		Overlay overlay = new Overlay();
		if ( displayStyle.startsWith("Contour"))
		{
			/*
			Duplicator duplicator = new Duplicator();
			ImagePlus imp_curSeg_Dilate = duplicator.run(imp_curSeg2);
			
			IJ.run(imp_curSeg_Dilate, "Maximum...", "radius=1");
			ImageCalculator ic = new ImageCalculator();
			imp_Contour = ic.run("Subtract create", imp_curSeg_Dilate, imp_curSeg2);
			*/
			
			Img<FloatType> imgCurSeg2 = ImageJFunctions.wrapFloat( imp_curSeg2 );
			Img<FloatType> imgContour = Utils.getLabelContour(imgCurSeg2, Utils.Connectivity.FACE );
			ImagePlus imp_Contour = ImageJFunctions.wrap(imgContour, "segmentation Contour");
			imp_Contour.setLut(segLut);
			imp_Contour.setDisplayRange( segDispRange[0], segDispRange[1]);
			
			ImageRoi imageRoi = new ImageRoi(0,0, imp_Contour.getProcessor() );
			imageRoi.setOpacity(0.75);
			imageRoi.setZeroTransparent(true);
			imageRoi.setPosition(pos[displayOrient]);
			overlay.add(imageRoi);
			
			//input_imp.setPosition(zSlice);
			input_ip.setLut(imageLut);
			impSegmentationDisplay.setProcessor( input_ip );
			impSegmentationDisplay.setDisplayRange(imageDispRange[0], imageDispRange[1]);
			
		}
		else if ( displayStyle.startsWith("Solid"))
		{
			ip_curSeg.setLut(segLut);
			ImageRoi imageRoi = new ImageRoi(0,0, ip_curSeg );
			imageRoi.setOpacity(0.5);
			imageRoi.setZeroTransparent(true);
			imageRoi.setPosition(pos[displayOrient]);
			overlay.add(imageRoi);
			
			input_ip.setLut(imageLut);
			impSegmentationDisplay.setProcessor( input_ip );
			impSegmentationDisplay.setDisplayRange(imageDispRange[0], imageDispRange[1]);

		}
		else
		{
			impSegmentationDisplay.setProcessor( ip_curSeg );
			impSegmentationDisplay.setDisplayRange( segDispRange[0], segDispRange[1]);
		}
		
		impSegmentationDisplay.setOverlay(overlay);
		impSegmentationDisplay.show();
		
		
		
	}
	
	
	
	
	protected void exportButton_callback(){
		
		if( initInterupted ){
			return;
		}
			
		boolean makeNewLabels = true ; 
		double hMin = previous.get("hMin");
		double thresh = previous.get("thresh");
		double peakFlooding = previous.get("peakFlooding");
		
		int nLabels = segmentTreeLabeler.updateTreeLabeling( (float)hMin , makeNewLabels);
		Img<IntType> export_img = segmentTreeLabeler.getLabelMap( (float)thresh , (float)peakFlooding);

		IntType minPixel = new IntType();
		IntType maxPixel = new IntType();
		ComputeMinMax<IntType> computeMinMax = new ComputeMinMax<>(export_img, minPixel, maxPixel);
		computeMinMax.process();

		ImagePlus exported_imp = ImageJFunctions.wrapFloat(export_img, imp0.getTitle() + " - watershed (h="+String.format("%5.2f", hMin)+", T="+String.format("%5.2f", thresh)+", %="+String.format("%2.0f", peakFlooding)+", n="+ maxPixel.get() +")" );
		
		int zMax=1;
		if( nDims==3 )
			zMax = (int)export_img.dimension(3); 
		
		exported_imp.setDimensions(1, zMax, 1);
		exported_imp.setOpenAsHyperStack(true);
		//LUT segmentationLUT = (LUT) imp_curSeg.getProcessor().getLut().clone();
		exported_imp.setLut(segLut);
		exported_imp.setDisplayRange(0, nLabels, 0);
		exported_imp.show();
		
		Recorder recorder =  Recorder.getInstance();  
		if( recorder != null ){
			if( !Recorder.scriptMode() ){
				Recorder.record("run","H_Watershed", "impin=[" + imp0.getTitle() + "] hmin=" + hMin + " thresh=" + thresh + " peakflooding=" + peakFlooding);
			}
			else{
				Recorder.recordCall("# @ImagePlus impIN");
				Recorder.recordCall("# @OpService ops");
				Recorder.recordCall("# @OUTPUT ImagePlus impOUT");
				
				Recorder.recordCall("impOUT = ops.run(\"H_Watershed\", impIN, "+hMin+", "+thresh+", "+peakFlooding+")");
			}
		}
		
	}
	
	
	@Override
	public void cancel(){
		// this function in never called in interactive command
		
	}

		
		
		
			
			
			
		
	
	
	
	public static <T extends RealType<T>> void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);
		
		
		
		// Launch the command .
		IJ.openImage("F:\\projects\\2DPlatynereis.tif").show();
		//Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2DEmbryoSection_Mette.tif");
		//Dataset dataset2 = (Dataset) ij.io().open("F:\\projects\\2D_8peaks.tif");
		//ij.ui().show(dataset);
		
		ij.command().run(Interactive_HWatershed.class, true);
		
		
	}


	

	@Override
	public void mouseDragged(MouseEvent e) {	}


	@Override
	public void mouseMoved(MouseEvent e) {
		if( getImageCanvas(e) == null){
			System.out.println("canvas is null");
			return;
		}
		if(impSegmentationDisplay == null){
			System.out.println("impSegmentationDisplay is null");
			return;
		}
		
				
		ImagePlus currentImage = getImageCanvas(e).getImage();
		if(   impSegmentationDisplay.equals( currentImage ) )
		{
			String statusStr = "";
			double[] vals = new double[] {getXPix(e),getYPix(e)};
			int count=0;
			String[] axisStr = new String[] {"x","y","z"};
			for(int d=0; d<nDims; d++){
				if(d != displayOrient){
					statusStr += ", "+axisStr[d]+"="+(vals[count]-1)*spacing[d];
					count++;
				}
				else if( nDims==3){
					statusStr += ", "+axisStr[d]+"="+(pos[displayOrient]-1)*spacing[displayOrient];
				}
			}	
			
			int imageValue = impSegmentationDisplay.getPixel( getXPix(e), getYPix(e) )[0];
			float imageValue2 = Float.intBitsToFloat(imageValue);
			statusStr += ", value=" + imageValue2;
			
			Overlay overlay = impSegmentationDisplay.getOverlay();
			if ( overlay != null )
				if(overlay.get(0) instanceof ImageRoi ){
					float[] upSampleFactors;
					if( upSample ){
						double minDispSpacing = Math.min( displaySpacing[0], displaySpacing[1] );
						upSampleFactors = new float[] {(float) (displaySpacing[0]/minDispSpacing) , (float) (displaySpacing[1]/minDispSpacing)};
					}
					else{
						upSampleFactors = new float[] { 1f, 1f };
					}
					int labelValue = imp_curSeg.getPixel( (int)(((float)getXPix(e))/upSampleFactors[0]), (int)(((float)getYPix(e))/upSampleFactors[1]) )[0];
					float labelValue2 = Float.intBitsToFloat(labelValue);
					statusStr += ", label=" + labelValue2;
				}
			
			IJ.showStatus( statusStr );
		}
		e.consume();
	}


	// helper, from Fiji abstract tool
	public ImageCanvas getImageCanvas(ComponentEvent e) {
		Component component = e.getComponent();
		return component instanceof ImageCanvas ? (ImageCanvas)component :
			(component instanceof ImageWindow ? ((ImageWindow)component).getCanvas() : null);
	}
	
	public int getXPix(MouseEvent e) {
		ImageCanvas canvas = getImageCanvas(e);
		return canvas == null ? -1 : canvas.offScreenX(e.getX());
	}

	public int getYPix(MouseEvent e) {
		ImageCanvas canvas = getImageCanvas(e);
		return canvas == null ? -1 : canvas.offScreenX(e.getY());
	}


}