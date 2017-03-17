package de.mpicbg.scf.InteractiveWatershed;


import java.io.File;
import java.util.Arrays;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.LutLoader;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.command.InteractiveImageCommand;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;
import org.scijava.module.MutableModuleItem;
import org.scijava.ItemIO;

import de.mpicbg.scf.InteractiveWatershed.HWatershedLabeling.Connectivity;

/**
 * An ImageJ2 command using the HWatershedLabelling class
 * 
 * known issue:
 * 	- using the B&C sometimes results in switching the image processor displayed (?)
 *  
 * TODO:
 *  - On image close or on plugin close message to confirm ending of the process (plus warn that non exported data will be lost)
 * 	- relabel the exported image (to get a more intuitive labeling)
 *	
 * Development ideas:
 *  - make sure only the necessary processing is done at each preview() method call
 *  - clean the code
 *  - allow to make intensity slider logarithmics or not
 *	- allow user to display other views switch between between xy, xz and yz view of the segmentation
 *		+ avoid need for new viewer infrastructure.
 *	- faster watershed to limit waiting time
 *	- identify bottleneck in ui update 
 *	- rethink the implementation for better readability and maintainability 
 *
 *	beyond the current plugin:
 *	- Given all the possible segment in the space (seed dynamics, flooding level) would it be possible to select the segments 
 *    with the best shapes given a simple shape model (feature vector on each regions)
 *  - probably not possible to explore all segments in the tree x threshold space
 *  	* build a new tree on the segment intersecting a given binarisation (i.e. segmentation on the dynamics hierarchy) 
 *  - Let user select some good/bad segment to create a shape/non-shape model
 *  - Let user correct some bad segmentation to create an improved merging criteria
 *  - learn a merging criteria
 * 
 */
@Plugin(type = Command.class, menuPath = "SCF>Labeling>Interactive watershed", initializer="initialize_HWatershed", headless = true, label="Interactive watershed")
//public class InteractiveMaxTree_<T extends RealType<T> > extends DynamicCommand implements Previewable  {
public class InteractiveWatershed_<T extends RealType<T> > extends InteractiveImageCommand implements Previewable  {

	// -- Parameters --
	@Parameter (type = ItemIO.BOTH)
	private	Dataset dataset;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Seed dynamics", stepSize="0.05")
	private Float seed_threshold;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Intensity threshold")
	private Float intensity_threshold;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="peak flooding (in %)", min="0", max="100")
	private Float peak_floodingPercentage;
	
	@Parameter(style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Image", "Contour overlay", "Solid overlay" } )
	private String displayStyle;
	
	@Parameter(label = "export", callback="exportButton_callback" )
	private Button testButton;
	
	@Parameter
	private OpService ops;
	
	
	
	// -- Other fields --
	float minI, maxI;
	HWatershedLabeling<FloatType> maxTreeConstructor;
	
	// init interface previous status
	
	int zSlice_Old = 1;
	Float seed_threshold_Old = 0f;
	Float intensity_threshold_Old = 0f;
	Float peak_floodingPercentage_Old = 100f;
	Float dispMin, dispMax;
	boolean firstPreview = true;
	String displayStyle_Old="";
	double[] input_displayRange=new double[2];
	double[] segmentation_displayRange=new double[2];
	int zSlice = 1;
	boolean is3D=false;
	
	int nLabel;
	ImagePlus impSegmentationDisplay;
	ImagePlus input_imp; // copy of the input dataset for displaying results
	ImagePlus imp_curSeg;
	
	
	// -- Command methods --

	@Override
	public void run() {    }


	// -- Initializer methods --
	protected void initialize_HWatershed() {	
		
		if (dataset == null)
		{
			return;
		}
		
		Img<FloatType> input = ops.convert().float32( (ImgPlus<T>) dataset.getImgPlus() );
		//input_imp =  ImageJFunctions.wrapFloat(input, "test");
		ImagePlus input_imp0 =  ImageJFunctions.wrapFloat(input, "test"); // this create a virtual stack that is not convenient for to catch a plane, hence the duplication on the following line
		input_imp = input_imp0.duplicate();
		
		ImagePlus imp0= IJ.getImage();
		input_displayRange[0] = (float)imp0.getDisplayRangeMin();
		input_displayRange[1] = (float)imp0.getDisplayRangeMax();
		
		float threshold = Float.NEGATIVE_INFINITY;
		
		
		maxTreeConstructor = new HWatershedLabeling<FloatType>(input, threshold, Connectivity.FACE);
		
		double[] dynamics = maxTreeConstructor.getTree().getFeature("dynamics");
		maxI = (float) Arrays.stream(dynamics).max().getAsDouble();
		minI = 0 ;
		
		final MutableModuleItem<Float> thresholdItem = getInfo().getMutableInput("seed_threshold", Float.class);
		thresholdItem.setMinimumValue( new Float(0) );
		thresholdItem.setMaximumValue( new Float(Math.log(maxI-minI+1)) );
		threshold = minI;
		thresholdItem.setStepSize( 0.05);
		
		final MutableModuleItem<Float> thresholdItem2 = getInfo().getMutableInput("intensity_threshold", Float.class);
		thresholdItem2.setMinimumValue( new Float(0) );
		thresholdItem2.setMaximumValue( new Float(Math.log(maxI-minI+1)) );
		intensity_threshold = minI;
		thresholdItem2.setStepSize( 0.05);
		
		final MutableModuleItem<Float> thresholdItem3 = getInfo().getMutableInput("peak_floodingPercentage", Float.class);
		thresholdItem3.setValue(this, 100f);
		
		nLabel =  maxTreeConstructor.getTree().getNumNodes();
		
		segmentation_displayRange[0] = 0;
		segmentation_displayRange[1] = 2*nLabel;
		
		int nDims = dataset.numDimensions();
		long[] dimensions = new long[nDims];
		dataset.dimensions(dimensions);
		if( dimensions.length==2 )
			impSegmentationDisplay = IJ.createImage("interactive watershed", (int)dimensions[0], (int)dimensions[1], 1, 32);
		else
			impSegmentationDisplay = IJ.createImage("interactive watershed", (int)dimensions[0], (int)dimensions[1], (int)dimensions[2], 32);
		
		impSegmentationDisplay.show();
		
		is3D = nDims>2?true:false;
		
		if (is3D )
		{
			ImagePlus.addImageListener( new ImageListener(){
								
								@Override
								public void imageClosed(ImagePlus imp) {
									System.out.println("image "+imp.getTitle()+" was closed");
								}
				
								@Override
								public void imageOpened(ImagePlus imp) {
									System.out.println("image "+imp.getTitle()+" was opened");	
								}
				
								@Override
								public void imageUpdated(ImagePlus imp) {
									ImagePlus.removeImageListener(this);
									
									System.out.println("image "+imp.getTitle()+" was updated");
									if ( imp==impSegmentationDisplay )
									{	
										zSlice = imp.getSlice();
										System.out.println("seg display: zSlice="+zSlice+" ; zSlice_Old="+zSlice_Old);
										if (zSlice != zSlice_Old )
										{	
											preview();
										}
									}
									
									ImagePlus.addImageListener(this);
										
										
								}
				
						});
			
			
			
		}
	}
	
	
	@Override
	public void preview(){
		
		
		LUT segmentationLUT;
		if( !firstPreview)
		{
			
			if (displayStyle_Old.startsWith("Contour") | displayStyle_Old.startsWith("Solid") )
			{
				input_displayRange[0] = impSegmentationDisplay.getDisplayRangeMin();
				input_displayRange[1] = impSegmentationDisplay.getDisplayRangeMax();
			}
			else
			{
				segmentation_displayRange[0] = impSegmentationDisplay.getDisplayRangeMin();
				segmentation_displayRange[1] = impSegmentationDisplay.getDisplayRangeMax();
			}	
			segmentationLUT = (LUT) imp_curSeg.getProcessor().getLut().clone();
		}
		else
			segmentationLUT = LutLoader.openLut( IJ.getDirectory("luts") + File.separator + "glasbey_inverted.lut");
		
		
		if( 		zSlice!=zSlice_Old 
				| 	seed_threshold != seed_threshold_Old 
				| 	intensity_threshold != intensity_threshold_Old
				|	peak_floodingPercentage != peak_floodingPercentage_Old)
		{
			Img<IntType> img_currentSegmentation = maxTreeConstructor.getHFlooding(	(float)Math.exp(seed_threshold)+minI-1, 
																						(float)Math.exp(intensity_threshold)+minI-1, 
																						peak_floodingPercentage, 
																						zSlice-1 	);
			imp_curSeg = ImageJFunctions.wrapFloat(img_currentSegmentation, "treeCut");
			
		}
		
		ImageProcessor ip_curSeg  = imp_curSeg.getProcessor();
		ip_curSeg.setLut( segmentationLUT );
		
		ImageProcessor input_ip;
		if(is3D)
			input_ip = input_imp.getStack().getProcessor(zSlice);
		else
			input_ip = input_imp.getProcessor();
		
		
		Overlay overlay = new Overlay();
		ImagePlus imp_Contour= null;
		if ( displayStyle.startsWith("Contour"))
		{
			
			Duplicator duplicator = new Duplicator();
			ImagePlus imp_curSeg_Dilate = duplicator.run(imp_curSeg);
			
			IJ.run(imp_curSeg_Dilate, "Maximum...", "radius=1");
			ImageCalculator ic = new ImageCalculator();
			imp_Contour = ic.run("Subtract create", imp_curSeg_Dilate, imp_curSeg);
			
			ImageRoi imageRoi = new ImageRoi(0,0, imp_Contour.getProcessor() );
			imageRoi.setOpacity(0.75);
			imageRoi.setZeroTransparent(true);
			imageRoi.setPosition(zSlice);
			overlay.add(imageRoi);
			
			input_imp.setPosition(zSlice);
			impSegmentationDisplay.setProcessor( input_ip );
			impSegmentationDisplay.setDisplayRange(input_displayRange[0], input_displayRange[1]);
			
		}
		else if ( displayStyle.startsWith("Solid"))
		{
			ImageRoi imageRoi = new ImageRoi(0,0, ip_curSeg );
			imageRoi.setOpacity(0.5);
			imageRoi.setZeroTransparent(true);
			imageRoi.setPosition(zSlice);
			overlay.add(imageRoi);

			impSegmentationDisplay.setProcessor( input_ip );
			impSegmentationDisplay.setDisplayRange(input_displayRange[0], input_displayRange[1]);

		}
		else
		{
			impSegmentationDisplay.setProcessor( ip_curSeg );
			impSegmentationDisplay.setDisplayRange( segmentation_displayRange[0], segmentation_displayRange[1]);
		}
		
		impSegmentationDisplay.setOverlay(overlay);
		impSegmentationDisplay.show();
		
		
		firstPreview = false;
		displayStyle_Old = displayStyle;
		zSlice_Old = zSlice;
		seed_threshold_Old = seed_threshold;
		intensity_threshold_Old = intensity_threshold;
		peak_floodingPercentage_Old = peak_floodingPercentage;
	}
	
	
	
	@Override
	public void cancel(){
		// this function in never called in interactive command
		
	}
	
	
	protected void exportButton_callback(){
		System.out.println("Export button click");
		
		float T  =(float)Math.exp(intensity_threshold)+minI-1;
		float h = (float)Math.exp(seed_threshold)+minI-1;
		float perc = peak_floodingPercentage; 
		Img<IntType> export_img = maxTreeConstructor.getHFlooding(	h, T, perc);
		ImagePlus exported_imp = ImageJFunctions.wrapFloat(export_img, dataset.getName() + " - watershed (h="+h+",T="+T+",%="+perc+")" );
		
		LUT segmentationLUT = (LUT) imp_curSeg.getProcessor().getLut().clone();
		exported_imp.setLut(segmentationLUT);
		exported_imp.show();
		
		
	}
	
	
	
	
	public static <T extends RealType<T>> void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);
		
		
		
		// Launch the command .
		//IJ.openImage("http://imagej.nih.gov/ij/images/blobs.gif").show();
		Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2DEmbryoSection_Mette.tif");//noise2000_blur2.tif");
		//Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2D_8peaks.tif");
		
		ij.ui().show(dataset);
		
		ij.command().run(InteractiveWatershed_.class, true);
		
		
	}


	
}