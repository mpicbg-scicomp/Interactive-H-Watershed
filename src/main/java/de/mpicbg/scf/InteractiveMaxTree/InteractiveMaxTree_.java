package de.mpicbg.scf.InteractiveMaxTree;

import java.util.Arrays;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import ij.ImagePlus;
import ij.IJ;

import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;
import org.scijava.command.DynamicCommand;
import org.scijava.display.DisplayService;
import org.scijava.module.MutableModuleItem;
import org.scijava.ItemIO;

import de.mpicbg.scf.InteractiveMaxTree.MaxTreeConstruction.Connectivity;
import de.mpicbg.scf.imgtools.image.create.image.ImagePlusImgConverter;

/**
 * An ImageJ2 command providing an interactive threshold chooser
 */
@Plugin(type = Command.class, menuPath = "SCF>test IJ2 command>MaxTree", initializer="initMaxTree", headless = true)
public class InteractiveMaxTree_<T extends RealType<T> > extends DynamicCommand implements Previewable  {

	// -- Parameters --
	@Parameter (type = ItemIO.BOTH)
	private	Dataset dataset;
	
	@Parameter(style = NumberWidget.SCROLL_BAR_STYLE, persist = false)
	private Float threshold;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private DisplayService displayService;
	
	// -- Other fields --
	float minI;
	float maxI;
	MaxTreeConstruction<FloatType> maxTreeConstructor;
	ImagePlusImgConverter impImgConverter;
	Img<FloatType> treeCut_labelMap;
	ImagePlus imp_hMax;
	int nLabel;
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
		Img<T> input = dataset.getImgPlus();
		
		
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
		
		final ImageDisplay imageDisplay = (ImageDisplay) displayService.createDisplay(input);
	}
	
	
	@Override
	public void preview(){
		treeCut_labelMap = maxTreeConstructor.getHMaxima(threshold);
		
		
		/*imp_hMax = impImgConverter.getImagePlus(treeCut_labelMap);
		imp_hMax.setTitle("hMax (h="+threshold+")");
		imp_hMax.show();
		IJ.run(imp_hMax, "3-3-2 RGB", "");
		IJ.setMinAndMax(imp_hMax, 0, nLabel);*/
	}
	
	@Override
	public void cancel(){
		if (imp == null)
		{
			return;
		}
		if( imp_hMax!=null )
		{
			imp_hMax.hide();
		}
	}
	
	
	public static void main(final String... args) throws Exception {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);
		
		
		
		// Launch the command .
		IJ.openImage("http://imagej.nih.gov/ij/images/blobs.gif").show();
		ij.command().run(InteractiveMaxTree_.class, true);
	}


	
}