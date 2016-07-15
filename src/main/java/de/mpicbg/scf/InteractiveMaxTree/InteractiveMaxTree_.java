package de.mpicbg.scf.InteractiveMaxTree;

import java.util.Arrays;

import net.imagej.Data;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.command.InteractiveImageCommand;
import net.imagej.display.DatasetView;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imagej.display.ColorTables;

import org.scijava.command.Command;
//import org.scijava.command.InteractiveCommand;
import org.scijava.command.Previewable;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;
//import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.ItemIO;

import de.mpicbg.scf.InteractiveMaxTree.MaxTreeConstruction.Connectivity;

/**
 * An ImageJ2 command providing an interactive threshold chooser
 */
@Plugin(type = Command.class, menuPath = "SCF>test IJ2 command>MaxTree", initializer="initMaxTree", headless = true)
//public class InteractiveMaxTree_<T extends RealType<T> > extends DynamicCommand implements Previewable  {
public class InteractiveMaxTree_<T extends RealType<T> > extends InteractiveImageCommand implements Previewable  {

	// -- Parameters --
	@Parameter (type = ItemIO.BOTH)
	private	Dataset dataset;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false)
	private Float threshold;
	
	@Parameter
	private ImageDisplayService imageDisplayService;
	
	@Parameter
	private OpService ops;
	
	@Parameter
	private DatasetService datasetService;
	
	
	// -- Other fields --
	float minI;
	float maxI;
	MaxTreeConstruction<FloatType> maxTreeConstructor;
	Dataset maxTreeDataset;
	int nLabel;
	ImageDisplay imageDisplay=null;
	
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
		
		float threshold = Float.NEGATIVE_INFINITY;
		
		
		
		maxTreeConstructor = new MaxTreeConstruction<FloatType>(input, threshold, Connectivity.FACE);
		
		double[] dynamics = maxTreeConstructor.getTree().getFeature("dynamics");
		maxI = (float) Arrays.stream(dynamics).max().getAsDouble();
		minI = 0 ;
		
		final MutableModuleItem<Float> thresholdItem = getInfo().getMutableInput("threshold", Float.class);
		thresholdItem.setMinimumValue( new Float(minI) );
		thresholdItem.setMaximumValue( new Float(maxI) );
		threshold = minI;
		thresholdItem.setStepSize( 1);
		
		nLabel =  maxTreeConstructor.getTree().getNumNodes();
		
		
		
		// create a view with a specific display range  and colortable
		Dataset inputDataset = datasetService.create(input); // create a dataset
		DatasetView dView = (DatasetView) imageDisplayService.createDataView((Data) inputDataset); // create a dataView for the dataset
		imageDisplay = (ImageDisplay) imageDisplayService.getDisplayService().createDisplay(dView); // create a display for the dataView
		
		int channel = dView.getIntPosition(Axes.CHANNEL);
		dView.setChannelRange(channel, 0, nLabel); // update the dataView intensity range
		dView.setColorTable(ColorTables.RGB332, channel); // update the dataView colorTable rk: imageDisplay needs to be created before
		
	}
	
	
	@Override
	public void preview(){
		
		Img<FloatType> treeCut = maxTreeConstructor.getHMaxima(threshold);
		ImgPlus<FloatType> treeCutPlus = new ImgPlus<FloatType>( treeCut );
		
		DatasetView dView  = (DatasetView) imageDisplay.get(0); // get view in the display
		dView.getData().setImgPlus( treeCutPlus ); // update image in the view

		
		
		
		/*imp_hMax = impImgConverter.getImagePlus(treeCut_labelMap);
		imp_hMax.setTitle("hMax (h="+threshold+")");
		imp_hMax.show();
		IJ.run(imp_hMax, "3-3-2 RGB", "");
		IJ.setMinAndMax(imp_hMax, 0, nLabel);*/
	}
	
	@Override
	public void cancel(){
		if (dataset == null)
		{
			return;
		}
		imageDisplay.close();
		
	}
	
	
	public static <T extends RealType<T>> void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);
		
		
		
		// Launch the command .
		//IJ.openImage("http://imagej.nih.gov/ij/images/blobs.gif").show();
		Dataset dataset = (Dataset) ij.io().open("F:\\projects\\noise2000_blur2.tif");
		
		ij.ui().show(dataset);
		
		ij.command().run(InteractiveMaxTree_.class, true);
	}


	
}