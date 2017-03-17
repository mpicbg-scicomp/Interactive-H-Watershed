package de.mpicbg.scf.InteractiveWatershed;

import net.imglib2.Cursor;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import net.imglib2.img.Img;


// if pos is updated tree labeling does not change
// if hMin is updated the segmentMap slice is constant but still need to be relabeled. currently we don't keep a copy and have to redo the clicing

// ideally multithread the copy of the segmentMap hyperslice (look how to optimize that)
// as well as the filling of the labelMap 

// the code definitely needs review there might be some confusion between input data and hyperslice


public class SegmentHierarchyToLabelMap <T extends RealType<T>> {

	Tree segmentTree;
	Img<IntType> segmentMap0;
	Img<T> intensity0;
	
	int[] nodeIdToLabel;  // current tree labelling
	
	Img<IntType> segmentMap; // current hyperslice
	Img<T> intensity; // current hyperslice
	
	
	public SegmentHierarchyToLabelMap(Tree segmentTree, Img<IntType> segmentMap0, Img<T> intensity0 ){
		
		this.segmentTree = segmentTree;
		this.segmentMap0 = segmentMap0;
		this.intensity0 = intensity0;
		
	}
	
	
	public void updateTreeLabeling(int hMin){
		nodeIdToLabel =  TreeUtils.getTreeLabeling(segmentTree, "dynamics", hMin  );
	}
	
	public Img<IntType> createLabelMap( int threshold, int percentFlooding){
		
		
		intensity = intensity0;
		segmentMap = segmentMap0.copy();
		Img<IntType> labelMap = fillLabelMap(threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	
	public Img<IntType> createLabelMap( int threshold, int percentFlooding, int dim, long pos){
		
		int nDims = segmentMap0.numDimensions();
		
		if (nDims>2)
		{	
			long[] dimensions = new long[nDims];
			segmentMap0.dimensions(dimensions);
			long[] newDimensions = new long[nDims];
			for ( int d = 0; d < nDims; ++d )
			{
				if(d<2)
					newDimensions[d] = dimensions[d];
				else
					newDimensions[d] = 1;
			}
			segmentMap = segmentMap0.factory().create(newDimensions, segmentMap0.firstElement());
			Cursor<IntType> cursor = segmentMap.cursor();
			Cursor<IntType> cursor0 = Views.hyperSlice(segmentMap0, dim, pos).cursor();
			
			while ( cursor.hasNext() )
				cursor.next().set( cursor0.next().get() );
			
			intensity = (Img<T>) Views.hyperSlice(intensity0, dim, pos);
		}
		else{
			segmentMap = segmentMap0.copy();
			intensity = intensity0;
		}
		
		Img<IntType> labelMap = fillLabelMap( threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	
	protected Img<IntType> fillLabelMap( int threshold, int percentFlooding ){
		
		double[] Imax = segmentTree.getFeature("Imax");
		
		int nNode = Imax.length;
		float[] peakThresholds = new float[nNode];
		for(int i=0;i<nNode; i++)
			peakThresholds[i] =  threshold + ((float)Imax[i]-threshold)*(1-percentFlooding/100);
		
		Cursor<IntType> cursor = segmentMap.cursor();
		Cursor<T> cursorImg = intensity.cursor();
		while( cursor.hasNext() )
		{
			T imgPixel = cursorImg.next();
			float val =imgPixel.getRealFloat();
			
			IntType pixel = cursor.next();
			int node = (int)pixel.getRealFloat();
			int label = nodeIdToLabel[node];
			
			if(  val >= threshold )
			{
				if(  val >= peakThresholds[label]  )
				{	
					float finalVal = (float)label;
					pixel.setReal( finalVal );
				}
				else
					pixel.setReal( 0.0 );
			}
			else
				pixel.setReal( 0.0 );
		}
		return segmentMap;
		
	}
}
