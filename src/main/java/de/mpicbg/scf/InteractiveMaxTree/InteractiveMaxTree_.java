package de.mpicbg.scf.InteractiveMaxTree;

import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.LutLoader;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.Dataset;
import net.imagej.DatasetService;
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
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;
import org.scijava.module.MutableModuleItem;
import org.scijava.ItemIO;

import de.mpicbg.scf.InteractiveMaxTree.MaxTreeConstruction.Connectivity;

/**
 * An ImageJ2 command using the MaxTree Construction class
 * 
 * TODO:
 *	- allow to export the labelmap (button, question when closing the plugin )
 *	- allow to make intensity slider logarithmics or not
 *	- Multithread image labeling (to keep some interactivity with very large plane)
 *		+ Remark: what is the bottleneck? tree labeling or image labeling
 *	- make sure only the necessayr processing is done at each preview() method call
 *  - clean the code
 *  - allow user to switch between between xy, xz and yz view of the segmentation
 *  
 * Development ideas:
 *  - Given all the possible segment in the space (seed dynamics, flooding level) would it be possible to select the segments 
 *    with the best shapes given a simple shape model (feature vector on each regions) 
 *  - Let user select some good/bad segment to create a shape model
 *  - Let user correct some bad segmentation to create an improved merging criteria
 *  	
 * 
 */
@Plugin(type = Command.class, menuPath = "SCF>test IJ2 command>MaxTree", initializer="initMaxTree", headless = true, label="Interactive watershed")
//public class InteractiveMaxTree_<T extends RealType<T> > extends DynamicCommand implements Previewable  {
public class InteractiveMaxTree_<T extends RealType<T> > extends InteractiveImageCommand implements Previewable  {

	// -- Parameters --
	@Parameter (type = ItemIO.BOTH)
	private	Dataset dataset;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Seed dynamics", stepSize="0.05")
	private Float seed_threshold;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="Intensity threshold")
	private Float intensity_threshold;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false, label="peak flooding (in %)", min="0", max="100")
	private Float peak_floodingPercentage=(float) 100;
	
	@Parameter(style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Image", "Contour overlay", "Solid overlay" } )
	private String displayStyle;
	
	//@Parameter
	//private ImageDisplayService imageDisplayService;
	
	@Parameter
	private OpService ops;
	
	@Parameter
	private DatasetService datasetService;
	
	
	// -- Other fields --
	float minI;
	float maxI;
	MaxTreeConstruction<FloatType> maxTreeConstructor;
	
	// init interface previous status
	
	int zSlice_Old = -1;
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
	ImagePlus input_imp; // wrapper of the input dataset for displaying results
	ImagePlus imp_curSeg;
	
	
	// -- Command methods --

	@Override
	public void run() {
		//IJ.run(imp, "Convert to Mask", "");
	}


	// -- Initializer methods --
	protected void initMaxTree() {	
		
		if (dataset == null)
		{
			return;
		}
		//Img<T> input0 = (Img<T>) dataset.getImgPlus();
		
		Img<FloatType> input = ops.convert().float32( (ImgPlus<T>) dataset.getImgPlus() );
		//input_imp =  ImageJFunctions.wrapFloat(input, "test");
		ImagePlus input_imp0 =  ImageJFunctions.wrapFloat(input, "test"); // this create a virtual stack that is not convenient for to catch a plane, hence the duplication on the following line
		input_imp = input_imp0.duplicate();
		
		ImagePlus imp0= IJ.getImage();
		input_displayRange[0] = (float)imp0.getDisplayRangeMin();
		input_displayRange[1] = (float)imp0.getDisplayRangeMax();
		
		float threshold = Float.NEGATIVE_INFINITY;
		
		
		maxTreeConstructor = new MaxTreeConstruction<FloatType>(input, threshold, Connectivity.FACE);
		
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
		thresholdItem3.setDefaultValue(100f);
		
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
		ImageWindow window_segmentation = impSegmentationDisplay.getWindow();
		
		is3D = nDims>2?true:false;
		
		if (is3D )
		{
			Scrollbar scrollBar = ( Scrollbar )( ( Panel )window_segmentation.getComponent( 1 ) ).getComponent( 1 );
		
			zSlice = scrollBar.getValue();
			scrollBar.addAdjustmentListener( new AdjustmentListener()
						{
							@Override
							public void adjustmentValueChanged(AdjustmentEvent e) {
								zSlice = e.getValue();
								
								//System.out.println("z = " + zSlice);
								// trigger update of the segmentation
								preview();
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
			Img<IntType> img_currentSegmentation = maxTreeConstructor.getHFlooding2(	(float)Math.exp(seed_threshold)+minI-1, 
																						(float)Math.exp(intensity_threshold)+minI-1, 
																						peak_floodingPercentage, 
																						zSlice-1 	);
			imp_curSeg = ImageJFunctions.wrapFloat(img_currentSegmentation, "treeCut");
			
		}
		
		ImageProcessor ip_curSeg  = imp_curSeg.getProcessor();
		ip_curSeg.setLut( segmentationLUT );
		
		ImageProcessor input_ip;
		if(is3D)
		{
			input_ip = input_imp.getStack().getProcessor(zSlice);
			//input_imp.show();
			//System.out.println("3D: z=" + zSlice);
		}
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
			//impSegmentationDisplay.getStack().setProcessor(ip_curSeg, zSlice);
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
		if (dataset == null)
		{
			return;
		}
		// create the full label map and display it
		
	}
	
	
	public static <T extends RealType<T>> void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);
		
		
		
		// Launch the command .
		//IJ.openImage("http://imagej.nih.gov/ij/images/blobs.gif").show();
		Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2DEmbryoSection_Mette.tif");//noise2000_blur2.tif");
		//Dataset dataset = (Dataset) ij.io().open("F:\\projects\\2D_8peaks.tif");
		
		ij.ui().show(dataset);
		
		ij.command().run(InteractiveMaxTree_.class, true);
	}


	
}