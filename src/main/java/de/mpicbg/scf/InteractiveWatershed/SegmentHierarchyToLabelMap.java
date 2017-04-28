package de.mpicbg.scf.InteractiveWatershed;

import java.util.HashSet;
import java.util.Set;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import net.imglib2.img.Img;


// if pos is updated tree labeling does not change
// if hMin is updated the segmentMap slice is constant but still need to be relabeled. currently we don't keep a copy and have to redo the clicking

// ideally multithread the copy of the segmentMap hyperslice (look how to optimize that)
// as well as the filling of the labelMap 

// the code definitely needs review there might be some confusion between input data and hyperslice


public class SegmentHierarchyToLabelMap <T extends RealType<T>> {

	Tree segmentTree0;
	Img<IntType> segmentMap0;
	Img<T> intensity0;
	
	int[] nodeIdToLabel;  // current tree labelling
	
	Img<IntType> segmentMap; // current hyperslice
	IterableInterval<T> intensity; // current hyperslice
	
	
	public SegmentHierarchyToLabelMap(Tree segmentTree, Img<IntType> segmentMap0, Img<T> intensity0 ){
		
		this.segmentTree0 = segmentTree;
		this.segmentMap0 = segmentMap0;
		this.intensity0 = intensity0;
		
	}
	

	public int updateTreeLabeling(float hMin ){
		boolean makeNewLabels = false;
		nodeIdToLabel =  TreeUtils.getTreeLabeling(segmentTree0, "dynamics", hMin, makeNewLabels  );
		return getNumberOfLabels();
	}

	public int updateTreeLabeling(float hMin, boolean makeNewLabels ){
		nodeIdToLabel =  TreeUtils.getTreeLabeling(segmentTree0, "dynamics", hMin, makeNewLabels  );
		return getNumberOfLabels();
	}
	
	private int getNumberOfLabels(){
		Set<Integer> labelSet = new HashSet<Integer>();
		if( nodeIdToLabel != null )
			for( int val : nodeIdToLabel)
				if( val>0)
					labelSet.add(val);
		return labelSet.size();
	}
	
	public Img<IntType> getLabelMap( float threshold, float percentFlooding){
		
		intensity = (IterableInterval<T>) intensity0;
		
		int nDims = segmentMap0.numDimensions();
		long[] dims = new long[nDims];
		segmentMap0.dimensions(dims);
		segmentMap = segmentMap0.factory().create(dims, segmentMap0.firstElement().createVariable() );
		Cursor<IntType> cursor = segmentMap.cursor();
		Cursor<IntType> cursor0 = segmentMap0.cursor();
		while(cursor0.hasNext()){
			cursor.next().set( cursor0.next().get() );
		}
		
		
		Img<IntType> labelMap = fillLabelMap(threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	
	public Img<IntType> getLabelMap( float threshold, float percentFlooding, int dim, long pos){
		
		int nDims = segmentMap0.numDimensions();
		
		if (nDims>2)
		{	
			long[] dimensions = new long[nDims];
			segmentMap0.dimensions(dimensions);
			long[] newDimensions = new long[nDims-1];
			int count = 0;
			for ( int d = 0; d < nDims ; ++d )
			{
				if(d!=dim){
					newDimensions[count] = dimensions[d];
					count++;
				}
				//else
					//newDimensions[d] = 1;
			}
			segmentMap = segmentMap0.factory().create(newDimensions, segmentMap0.firstElement().createVariable());
			Cursor<IntType> cursor = segmentMap.cursor();
			Cursor<IntType> cursor0 = Views.hyperSlice(segmentMap0, dim, pos).cursor();
			
			while ( cursor.hasNext() )
				cursor.next().set( cursor0.next().get() );
			
			intensity = (IterableInterval<T>) Views.hyperSlice(intensity0, dim, pos);
		}
		else{
			long[] dims = new long[nDims];
			segmentMap0.dimensions(dims);
			segmentMap = segmentMap0.factory().create(dims, segmentMap0.firstElement().createVariable() );
			Cursor<IntType> cursor = segmentMap.cursor();
			Cursor<IntType> cursor0 = segmentMap0.cursor();
			while(cursor0.hasNext()){
				cursor.next().set( cursor0.next().get() );
			}
			intensity = (IterableInterval<T>) intensity0;
		}
		
		Img<IntType> labelMap = fillLabelMap( threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	
	protected Img<IntType> fillLabelMap( float threshold, float percentFlooding ){
		
		double[] Imax = segmentTree0.getFeature("Imax");
		
		int nNode = Imax.length;
		float[] peakThresholds = new float[nNode];
		for(int i=0;i<nNode; i++)
			peakThresholds[i] =  threshold + ((float)Imax[i]-threshold)*(1f-percentFlooding/100f);
		
		Cursor<IntType> cursor = segmentMap.cursor();
		Cursor<T> cursorImg = intensity.cursor();
		while( cursor.hasNext() )
		{
			T imgPixel = cursorImg.next();
			float val =imgPixel.getRealFloat();
			
			IntType pixel = cursor.next();
			int node = (int)pixel.getRealFloat();
			int label = nodeIdToLabel[node];
			int labelRoot = segmentTree0.nodes.get(node).labelRoot;
			if(  val >= threshold )
			{
				if(  val >= peakThresholds[labelRoot]  )
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
