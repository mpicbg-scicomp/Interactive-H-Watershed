package de.mpicbg.scf.InteractiveMaxTree;


import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import de.mpicbg.scf.InteractiveMaxTree.HierarchicalFIFO;
import de.mpicbg.scf.InteractiveMaxTree.Tree;
import de.mpicbg.scf.InteractiveMaxTree.TreeLabeling;
import de.mpicbg.scf.imgtools.image.create.image.ImagePlusImgConverter;
import de.mpicbg.scf.imgtools.image.create.labelmap.LocalMaximaLabeling;
import de.mpicbg.scf.imgtools.image.neighborhood.ImageConnectivity;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;


/**
 * 
 * @author Lombardot Benoit, Scientific Computing Facility, MPI-CBG, Dresden
 * 2016/07/05: First implementation of the max tree  
 * 
 * Remark: the input image is duplicated and will not be modified during the process 
 * Remark: the input type should be able to represent the number of nodes of the tree number (2 time the number of maxima in input minus one) 
 * 
 * @param <T>
 */
public class MaxTreeConstruction<T extends RealType<T>> {
	// Build max tree based on IFT watershed approach
	//
	// max tree is hierarchy of image peaks based on their dynamics
	// each maximum is a leaf of the tree and used to initiate the immersion process
	// when 2 peaks meet at a saddle their dynamics is calculated and a new node is created 
	// 
	// one can cut through the tree to define H-Seeds (the set of peaks with minimal dynamics while being superior to H)
	// these can be used to create a H-Segments (segments obtained by flooding h-Seeds)
	// one can prune the tree or cut its base
	// one can select a subset of nodes minimizing a parameter (or a set of parameter)
	// 
	// To be explored: 
	//   - is there a way to easily extend imgLabelling to integrate the tree structure (treeLabelling)
	//   - filters for tree Labeling
	//   rk: is there an easy way to visualize imgLabeling
	//
	// Input: I
	// Output: Label (i.e. I), Parent, Hcriteria
	// algo will discretize value
	//
	// I_localMin = local_minima(I)
	// Imin = min(I); Imax = max(I)
	// Imin = max(Imin, Thresh)
	// Q = Queue labeled pixel in a priority queue with FIFO policy
	// define out of bound to have value Imin-1
	// initialize parent to -I+Imin if labeled, 0 otherwise // size wise parent will 2*max(I_localMin)-1
	// initialize Hcriteria to 0
	// initialize isDequeued to false
	//while Q.hasNext()
	//
	//	p=Q.next()
	//  isdeQueued[p]=true;
	//
	//	if parent(pLabel)>0   // relabel pixel with current root label, root could have change since the queuing
	//		pLabel = findRoot(pLabel)
	//
	//	pVal = p.getVal();
	//	for n in N(p)
	//     if n was already dequeued
	//      nLabel = finRoot(nLabel)
	//		if nLabel != pLabel // 2 labels are meeting at that point, create a new label and measure peaks height
	//			maxLabel++
	//			Hcriteria(nLabel) = Imax(nLabel)-pVal + Hcriteria(nLabel) // Imax can be stored in the parent array
	//			parent(nLabel)= maxLabel
	//			Hcriteria(pLabel) = Imax(pLabel)-pVal + Hcriteria(pLabel)
	//			parent(pLabel)= maxLabel
	//			pLabel = maxLabel
	//			parent(maxLabel)= -pVal+2*Imin-1			
	//			Hcriteria(maxLabel) = max( Hcriteria(pLabel) , Hcriteria(nLabel) )
	//
	//		if nVal>Imin // not queued and in-bound
	//			Q.add(n, nVal)
	//			I(n)=pLabel
	//			   
	
	
	
	public enum Connectivity
	{
		FACE(ImageConnectivity.Connectivity.FACE),
		FULL(ImageConnectivity.Connectivity.FULL);
		
		ImageConnectivity.Connectivity conn;
		
		Connectivity(ImageConnectivity.Connectivity conn)
		{			
			this.conn = conn;
		}
		
		ImageConnectivity.Connectivity getConn()
		{
			return conn;
		}
	}
	
	
	
	//private double[] hCriteria; // to be initialized at 0
	//private float[] Imax; // to be initialized at 0
	//private int[] parent; // to be initialized at minus one (rk it could contain Icriteria which is needed only before parent definition)
	//private int[][] children;
	private Img<T> labelMap_treeNodes;
	private float threshold;
	private Connectivity connectivity;
	private boolean maxTreeIsBuilt=false;
	//private int nLeaves=0;
	private Tree maxTree;
	
	
	
	public MaxTreeConstruction(Img<T> input, float threshold, Connectivity connectivity)
	{
		this.labelMap_treeNodes = input.copy();
		this.threshold = threshold;
		this.connectivity = connectivity;
	}
	
	
	
	// getter ...
	
	public Tree getTree() {
		createMaxTree();
		return maxTree;
	}

	/*public int[] getParents() {
		createMaxTree();
		return parent;
	}*/

	public Img<T> getLabelMap() {
		createMaxTree();
		return labelMap_treeNodes;
	}


	//public Img<IntType> labelMapDebug; // debug only

	
	protected void createMaxTree()
	{
		if ( maxTreeIsBuilt )
			return;
		
		T Tmin = labelMap_treeNodes.randomAccess().get().createVariable();
		T Tmax = Tmin.createVariable();		
		ComputeMinMax.computeMinMax(labelMap_treeNodes, Tmin, Tmax);
		float min = Math.max(threshold, Tmin.getRealFloat());
		float max = Tmax.getRealFloat();
		
		// get local maxima
		LocalMaximaLabeling maxLabeler = new LocalMaximaLabeling();
		Img<IntType> seed = maxLabeler.LocalMaxima(labelMap_treeNodes,min);	
		IntType TnSeeds = new IntType(0);
		IntType Tdummy = new IntType(0);
		ComputeMinMax.computeMinMax(seed, Tdummy, TnSeeds);
		int nLeaves = (int) TnSeeds.getRealFloat();
		
		// debug
		//labelMapDebug = seed;
		// debug
		
		// create a priority queue
		HierarchicalFIFO Q = new HierarchicalFIFO( (int)min, (int)max, (int)(max-min+1));
		
		int ndim = labelMap_treeNodes.numDimensions();
		long[] dimensions = new long[ndim]; labelMap_treeNodes.dimensions(dimensions);
		
		// create a flat iterable cursor
		long[] minInt = new long[ ndim ], maxInt = new long[ ndim ];
		for ( int d = 0; d < ndim; ++d ){   minInt[ d ] = 0 ;    maxInt[ d ] = dimensions[d] - 1 ;  }
		FinalInterval interval = new FinalInterval( minInt, maxInt );
		final Cursor< T > input_cursor = Views.flatIterable( Views.interval( labelMap_treeNodes, interval)).cursor();
		final Cursor< IntType > seed_cursor = Views.flatIterable( Views.interval( seed, interval)).cursor();
		
		double[] hCriteria = new double[2*nLeaves];
		double[] Imax = new double[2*nLeaves];
		int[] parent = new int[2*nLeaves];
		int[][] children = new int[2*nLeaves][];
		for(int i=0; i<hCriteria.length; i++)
		{
			children[i] = new int[] {-1,-1};
			parent[i]=i;
			hCriteria[i]=0;
			Imax[i]=0;
		}
		// fill the queue
		int idx=-1;
		while( input_cursor.hasNext() )
		{
			++idx;
			T pInput = input_cursor.next();
			float pVal = pInput.getRealFloat();
			float valSeed = seed_cursor.next().getRealFloat(); 
			if ( pVal>=min)
			{
				if ( valSeed>0)
				{
					Q.add( idx, (int)pVal );
					pInput.setReal(min-1-valSeed);
					Imax[(int)valSeed]= pVal;
				}
			}
			else
			{
				pInput.setReal(min-1);
			}
		}
		
		
		// extend input and seeds to to deal with out of bound
		T outOfBoundT = labelMap_treeNodes.firstElement().createVariable(); 
		outOfBoundT.setReal(min-1);
		RandomAccess< T > input_XRA = Views.extendValue(labelMap_treeNodes, outOfBoundT ).randomAccess();
		RandomAccess< T > input_XRA2 = input_XRA.copyRandomAccess();
		
		// define the connectivity
		long[][] neigh = ImageConnectivity.getConnectivityPos(ndim, connectivity.getConn() );
		int[] n_offset = ImageConnectivity.getIdxOffsetToCenterPix(neigh, dimensions);
		long[][] dPosList = ImageConnectivity.getSuccessiveMove(neigh);
		int nNeigh = n_offset.length;
		
		
		boolean[] isDequeued = new boolean[(int)labelMap_treeNodes.size()];
		for (int i=0; i<isDequeued.length; i++)
			isDequeued[i]=false;
			
		int maxLabel = nLeaves;
		while( Q.HasNext() )
		{ 	
			
			final int pIdx = (int) Q.Next(); 
			final int pVal = Q.getCurrent_level() + Q.getMin();
			
			final long[] posCurrent = new long[ndim];
			getPosFromIdx((long)pIdx, posCurrent, dimensions);
			input_XRA.setPosition(posCurrent);
			T p = input_XRA.get();
			int pLabel = (int)(min - 1 - p.getRealFloat());
			
			isDequeued[pIdx]=true;
			
			
			// relabel p if its parent changed (peaks was fused) since initially labeled
			int pLabel_parent = (int)parent[pLabel];
			while (pLabel_parent!=pLabel)  // find the root of plabel and relabel p according to it (rk: the root necessarily has highest value than p )
			{
				pLabel = (int)pLabel_parent;
				pLabel_parent=parent[pLabel];
			}
			p.setReal(min-1-pLabel);
			
			
			// loop on neighbors			
			input_XRA2.setPosition(posCurrent);
			for( int i=0; i<nNeigh; i++)
			{
				final int nIdx = pIdx + n_offset[i];
				
				input_XRA2.move(dPosList[i]);
				final T n = input_XRA2.get();
				final float nVal = n.getRealFloat();
				
				if ( nVal != (min-1) ) // if n is in-bound
				{
					if( isDequeued[nIdx] ) // p is the lowest point 
					{	
						int nLabel = (int)(min - 1 - nVal);
						int nLabel_parent = parent[ nLabel ];
						while (nLabel_parent!=nLabel) // find the root of nLabel
						{
							nLabel = nLabel_parent;
							nLabel_parent=parent[nLabel];
						}
						if( nLabel != pLabel ) // 2 distincts label roots are meeting and p is the saddle : initialize a new peak a new label and measure peaks height
						{						
												 
							maxLabel++;
							hCriteria[nLabel] = Imax[nLabel]-pVal; 
							parent[nLabel]= maxLabel;
							hCriteria[pLabel] = Imax[pLabel]-pVal;
							parent[pLabel]= maxLabel;
							children[maxLabel][0]= nLabel;
							children[maxLabel][1]= pLabel;
							
							Imax[maxLabel]= Math.max(Imax[pLabel], Imax[nLabel]);
							pLabel = maxLabel;

							hCriteria[maxLabel] = Imax[maxLabel]-pVal;
							p.setReal(min-1-pLabel);
						}
					}
					
					if ( nVal>=min ) // is not queued yet and is in bound?
					{
						Q.add( nIdx, (int)nVal );
						n.setReal(min -1 - pLabel);
					}
				}
				
				
			} // end loop on neighbor
			
		} // end while
		
		// for root nodes adjust there height to Imax(rootLabel)-min. 
		for(int i=0 ; i<parent.length; i++)
		{
			if( hCriteria[i]>0 & parent[i]==i)
			{
				hCriteria[i] = Imax[i]-min;
			}
		}
		
		// convert the input to label image (label L is stored in input with value min-1-L all other value should be equal to min-1 )
		final T minT = labelMap_treeNodes.firstElement().createVariable();
        minT.setReal(min-1);
        final T minusOneT = labelMap_treeNodes.firstElement().createVariable();
        minusOneT.setReal(-1);
        Cursor<T> input_cursor2 = labelMap_treeNodes.cursor();
        while( input_cursor2.hasNext() )
		{
        	T p = input_cursor2.next();
        	if (p.getRealFloat()>=(min-1) )
        	{
        		p.setReal(0);
        	}
        	else
        	{
        		p.sub(minT);
            	p.mul(minusOneT);
        	}
		}
		
        maxTree = new Tree(parent, children);
        maxTree.setFeature("dynamics", hCriteria );
        maxTree.setFeature("Imax", Imax );
        
        
        
        maxTreeIsBuilt=true;
        
        return;
        // at the end, input was tranformed to a label image
        // hCriteria contains the dynamics of each peak
        // parent link nodes to their parent node, if label L has no parent, parent[L]=0 
        // these can be used to build any hMap on the fly.
	}
	
	
	protected static void getPosFromIdx(long idx, long[] position, long[] dimensions)
	{
		for ( int i = 0; i < dimensions.length; i++ )
		{
			position[ i ] = ( int ) ( idx % dimensions[ i ] );
			idx /= dimensions[ i ];
		}
	}
	

	protected Img<T> relabel_labelMapTreeNodes(Img<T> labelMap_treeNodes, int[] nodeToLabel)
	{
		Img<T> labelMap = labelMap_treeNodes.copy();
		Cursor<T> cursor = labelMap.cursor();
		while( cursor.hasNext() )
		{
			T pixel = cursor.next();
			float val = (float)nodeToLabel[(int)pixel.getRealFloat()];
			pixel.setReal( val );	
		}
		return labelMap;
	}

	
	
/*	
	protected int[] pruneTree(float hMin)
	{
		
		nodeToLabel = new int[2*nLeaves];
		for(int node=0; node<nodeToLabel.length; node++)
			nodeToLabel[node]=0;
		
		// find root nodes
		ArrayList<Integer> roots = new ArrayList<Integer>();
		for(int node=0; node<nodeToLabel.length; node++)
			if( parent[node] == node )
				roots.add(node);
		
		// from each root go down the tree
		// and start a new label if the current node is a leaf
		// or if all my children do not pass the test
		for( int node : roots)
			if( passTest(node) )
				analyzeNode_MaxRule(node);
				
		
		return nodeToLabel;
	}
*/	
	
/*	
	protected void analyzeNode_MaxRule(int node)
	{
		if( isLeaf(node) )
		{
			nodeToLabel[node]=node;// label
		}
		else{
			int[] c = children[node]; 
			boolean test0 = passTest(c[0]); 
			boolean test1 = passTest(c[1]);
			if( !test0 & !test1 ) // if none of the children pass the test label node and its descendant with value node
			{
				nodeToLabel[node]=node;
				relabelOffsprings(node,node);	
			}
			else
			{
				if( test0 )
					analyzeNode_MaxRule(c[0]);
				if( test1 )
					analyzeNode_MaxRule(c[1]);
			}			
		}	
	}
	
	protected boolean passTest(int node)
	{
		if(hCriteria[node]>hMin)
			return true;
		return false;
	}
	
	
	protected boolean isLeaf(int node)
	{
		return ( children[node][0]==-1 & children[node][1]==-1 ); 
	}
	
	// assumes node is already labeled
	protected void relabelOffsprings(int node, int label)
	{
		if( !isLeaf(node) )	
			for( int c : children[node])
			{
				nodeToLabel[c]=label;
				relabelOffsprings(c,label);
			}
		return;
	}
	
*/	
	/*
	// tree filtering before relabeling
	protected int[] filterMaxTree_H(float hMin)
	{	
		int[] nodeToLabel = new int[2*nLeaves];
		// initialize each node to inactive
		for(int node=0; node<nodeToLabel.length; node++)
			nodeToLabel[node]=-1;
		
		for(int node=0; node<=nLeaves; node++)
		{
			analyzeNode(node, hMin, nodeToLabel);
		}
		
		for(int node=0; node<nodeToLabel.length; node++)
			if ( nodeToLabel[node] == -1 )
					nodeToLabel[node]=0;
		
		return nodeToLabel;
	}
	
	
	
	protected void analyzeNode(int node, float hMin, int[] nodeToLabel)
	{
		if( nodeToLabel[node]<0) // if the node is still active
		{
			boolean testResult = hCriteria[node]>hMin;
			
			if ( testResult ) // choose the labeling and relabel its descendants
			{
				nodeToLabel[node] = node;
				relabelChildrenNodes(children[node][0], node, nodeToLabel);
				relabelChildrenNodes(children[node][1], node, nodeToLabel);
			}
			else if ( parent[node] != node ) // go up the tree searching for a valid node
			{
				analyzeNode( parent[node], hMin, nodeToLabel);
			}
		}
	}
	
	
	protected void relabelChildrenNodes(int node, int newLabel, int[] nodeToLabel)
	{
		if (node>=0)
		{
			nodeToLabel[node]= newLabel;
			relabelChildrenNodes(children[node][0],newLabel, nodeToLabel);
			relabelChildrenNodes(children[node][1],newLabel, nodeToLabel);
		}
		return;
	}
	*/
	
	public Img<T>  getHMaxima(float hMin)
	{
		
		boolean[] nodeSelection = new boolean[maxTree.getNumNodes()];
		double[] feature = maxTree.getFeature("dynamics");
		
		for( int nodeId=0; nodeId<maxTree.getNumNodes(); nodeId++ )
			nodeSelection[nodeId] = feature[nodeId]>hMin; 
			
		TreeLabeling treeLabeler = new TreeLabeling(maxTree);
		int[] nodeIdToLabel =  treeLabeler.getNodeIdToLabel_LUT(nodeSelection, TreeLabeling.Rule.MAX );
		Img<T> TreeCutMap = relabel_labelMapTreeNodes(labelMap_treeNodes, nodeIdToLabel);
		
		return TreeCutMap;
	}
	
	
	
	
	public static void main(final String... args)
	{
		// image to flood
		new ij.ImageJ();
		IJ.open("F:\\projects\\noise500.tif");
		//IJ.open("F:\\projects\\1D_3peaks.tif");
		ImagePlus imp = IJ.getImage();
		//IJ.run(imp, "Gaussian Blur...", "sigma=2");
		ImagePlusImgConverter impImgConverter = new ImagePlusImgConverter(imp);
		Img<FloatType> input = impImgConverter.getImgFloatType();
		
		float threshold = Float.NEGATIVE_INFINITY;
		MaxTreeConstruction<FloatType> maxTreeConstructor = new MaxTreeConstruction<FloatType>(input, threshold, Connectivity.FULL);
		
		Img<FloatType> output = maxTreeConstructor.getLabelMap();
		ImagePlus imp_out = impImgConverter.getImagePlus(output);
		imp_out.show();
		
		//impImgConverter.getImagePlus( maxTreeConstructor.labelMapDebug ).show();
		
		int[] parents = maxTreeConstructor.getTree().getParentsAsArray();
		System.out.println("Parents: " + Arrays.toString(parents));
		
		double[] attributes = maxTreeConstructor.getTree().getFeature("dynamics");
		System.out.println("Attributes: " + Arrays.toString(attributes));
		
		
		
		float[] attributes2 = new float[] {1, 2, 4, 8, 16, 32};//Arrays.copyOf(attributes, attributes.length);
		//Arrays.sort(attributes2);
		for( float val : attributes2 )
		{
			float h= val+1;
			Img<FloatType> hMax = maxTreeConstructor.getHMaxima(h);
			ImagePlus imp_hMax = impImgConverter.getImagePlus(hMax);
			imp_hMax.setTitle("hMax (h="+h+")");
			imp_hMax.show();
			IJ.run(imp_hMax, "3-3-2 RGB", "");
			IJ.setMinAndMax(imp_hMax, 0, parents.length);
		}
		
		
		
		
	}
	

}
