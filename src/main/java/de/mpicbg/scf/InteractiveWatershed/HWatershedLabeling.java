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


import de.mpicbg.scf.InteractiveWatershed.HierarchicalFIFO;
import de.mpicbg.scf.InteractiveWatershed.Tree;

import de.mpicbg.scf.InteractiveWatershed.imgTools.LocalMaximaLabeling;
import de.mpicbg.scf.InteractiveWatershed.imgTools.ImageConnectivity;
import de.mpicbg.scf.InteractiveWatershed.imgTools.ProgressDialog;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;





public class HWatershedLabeling<T extends RealType<T>> {
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
	
	
	
	// is there a version where watershed and tree construction are done independently
	// would allow to use fast watershed and to choose different similarity measure
	//  see Edelsbrunner strategy for mesh simplification
	//	or before see algorithm for region merging based on similarity
	
	
	
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
	
	private Img<IntType> labelMapMaxTree;
	private float threshold;
	private Connectivity connectivity;
	private boolean maxTreeIsBuilt=false;
	private Tree maxTree;
	private boolean  wasCancelled=false;
	
	
	public HWatershedLabeling(Img<T> input, float threshold, Connectivity connectivity)
	{
		int nDims = input.numDimensions();
		long[] dims = new long[nDims];
		input.dimensions(dims);
		ImgFactory<IntType> imgFactoryIntType=null;
		try {
			imgFactoryIntType = input.factory().imgFactory( new IntType() );
		} catch (IncompatibleTypeException e) {
			e.printStackTrace();
		}
		
		if ( imgFactoryIntType != null )
		{
			this.labelMapMaxTree = imgFactoryIntType.create(dims, new IntType(0));
			Cursor<IntType> c_label = labelMapMaxTree.cursor();
			Cursor<T>       c_input = input.cursor();
			while( c_input.hasNext() )
			{
				c_label.next().setInteger( (int) c_input.next().getRealFloat() );
			}
		}
		
		this.threshold = threshold;
		this.connectivity = connectivity;
	}
	
	
	
	// getter ...
	
	public Tree getTree() {
		createMaxTree2();
		return maxTree;
	}

	public Img<IntType> getLabelMapMaxTree() {
		createMaxTree2();
		return labelMapMaxTree;
	}


	@Deprecated
	protected void createMaxTree()
	{
		if ( maxTreeIsBuilt )
			return;
		
		IntType Tmin = labelMapMaxTree.randomAccess().get().createVariable();
		IntType Tmax = Tmin.createVariable();		
		ComputeMinMax.computeMinMax(labelMapMaxTree, Tmin, Tmax);
		float min = Math.max(threshold, Tmin.getRealFloat());
		float max = Tmax.getRealFloat();
		
		// get local maxima
		LocalMaximaLabeling maxLabeler = new LocalMaximaLabeling();
		Img<IntType> seed = maxLabeler.LocalMaxima(labelMapMaxTree,min);	
		IntType TnSeeds = new IntType(0);
		IntType Tdummy = new IntType(0);
		ComputeMinMax.computeMinMax(seed, Tdummy, TnSeeds);
		int nLeaves = (int) TnSeeds.getRealFloat();
		
		// debug
		//labelMapDebug = seed;
		// debug
		
		// create a priority queue
		HierarchicalFIFO Q = new HierarchicalFIFO( (int)min, (int)max);
		
		int ndim = labelMapMaxTree.numDimensions();
		long[] dimensions = new long[ndim]; labelMapMaxTree.dimensions(dimensions);
		
		// create a flat iterable cursor
		long[] minInt = new long[ ndim ], maxInt = new long[ ndim ];
		for ( int d = 0; d < ndim; ++d ){   minInt[ d ] = 0 ;    maxInt[ d ] = dimensions[d] - 1 ;  }
		FinalInterval interval = new FinalInterval( minInt, maxInt );
		final Cursor< IntType > input_cursor = Views.flatIterable( Views.interval( labelMapMaxTree, interval)).cursor();
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
			IntType pInput = input_cursor.next();
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
		IntType outOfBoundT = labelMapMaxTree.firstElement().createVariable(); 
		outOfBoundT.setReal(min-1);
		RandomAccess< IntType > input_XRA = Views.extendValue(labelMapMaxTree, outOfBoundT ).randomAccess();
		RandomAccess< IntType > input_XRA2 = input_XRA.copyRandomAccess();
		
		// define the connectivity
		long[][] neigh = ImageConnectivity.getConnectivityPos(ndim, connectivity.getConn() );
		int[] n_offset = ImageConnectivity.getIdxOffsetToCenterPix(neigh, dimensions);
		long[][] dPosList = ImageConnectivity.getSuccessiveMove(neigh);
		int nNeigh = n_offset.length;
		
		
		boolean[] isDequeued = new boolean[(int)labelMapMaxTree.size()];
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
			IntType p = input_XRA.get();
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
				final IntType n = input_XRA2.get();
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
		final IntType minT = labelMapMaxTree.firstElement().createVariable();
        minT.setReal(min-1);
        final IntType minusOneT = labelMapMaxTree.firstElement().createVariable();
        minusOneT.setReal(-1);
        Cursor<IntType> input_cursor2 = labelMapMaxTree.cursor();
        while( input_cursor2.hasNext() )
		{
        	IntType p = input_cursor2.next();
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
	
	
	// version 2: 
	//	- provide necessary info for interactive plotting of any watershed with h and I thresholds 
	//	- adjust tree construction	
	
	// Input: I
	// Output: watershed label, Parent, Hcriteria, Imax,
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
	// while Q.hasNext()
	//
	//	p=Q.next()
	//  isdeQueued[p]=true;
	//
	//	pLeaf= p.getlabel()
	//	pRoot= findRoot(pLeaf)
	//
	//	pVal = p.getVal();
	//	for n in N(p)
	//     if n was already dequeued // meaning that p is the lowest point()
	//      nLeaf = n.getLabel() 
	//		nNode = finRoot(nLabel)
	//		if nNode != pNode // 2 nodes are meeting at that point, create a new node 
	//			maxNode++ // replace maxNode by newNode
	//			Hcriteria(nNode) = Imax(nNode)-pVal  
	//			Hcriteria(pNode) = Imax(pNode)-pVal
	//			
	//			//merge the smaller peak with first neighbor node that has higher dynamics
	//			if H(nNode)>=H(pNode)
	//              node1 = pLeaf
	//				node = nLeaf
	//				while(H(par(node))<=H(node1))
	//					node=parent(node)
	//				merge(node, node1, maxNode)
	//			else 
	//				node1 = nLeaf
	//				node = pLeaf
	//				while(H(parent(node))<=H(node1))
	//					node=parent(node)
	//				merge(node, node1, maxNode)
	//			Imax(maxNode) = max( Imax(node), Imax(node1) )
	//			H[maxNode] = Imax[maxNode]-pVal;
	//
	//		if nVal>Imin // not queued and in-bound
	//			Q.add(n, nVal)
	//			I(n)=pLeaf
	//
	// merge(node1, node2, newNode) // updates parent, children
	// {
	//	parent(newNode) = newNode
	//	
	//	// update children of parent(node1) if needed
	//	p1 = parent[node1]
	//	if p1!=node1 // node1 is note root
	//		for (i=0, i<2; i++)
	//			if children[p1][i] == node1
	//				children[p1][i] = newNode
	//
	//	// update the children of parent(node2) if needed 
	//	p2 = parent[node2]
	//	if ...
	//
	//	parent(node1) = newNode
	//	parent(node2) = newNode
	// }
	//
	
	protected void createMaxTree2()
	{
		if ( maxTreeIsBuilt | wasCancelled)
			return;
		
		ProgressDialog.reset();
		
		//////////////////////////////////////////////////////////////////////
		// initialisation ////////////////////////////////////////////////////
		ProgressDialog.setStatusText("HWatershed: Initialisation");
		ProgressDialog.setProgress( 0 );
		
		IntType Tmin = labelMapMaxTree.randomAccess().get().createVariable();
		IntType Tmax = Tmin.createVariable();		
		ComputeMinMax.computeMinMax(labelMapMaxTree, Tmin, Tmax);
		float min = Math.max(threshold, Tmin.getRealFloat());
		float max = Tmax.getRealFloat();
		
		// get local maxima (8/26 connected by default)
		LocalMaximaLabeling maxLabeler = new LocalMaximaLabeling();
		Img<IntType> seed = maxLabeler.LocalMaxima(labelMapMaxTree,min);	
		IntType TnSeeds = new IntType(0);
		IntType Tdummy = new IntType(0);
		ComputeMinMax.computeMinMax(seed, Tdummy, TnSeeds);
		int nLeaves = (int) TnSeeds.getRealFloat();
		
		
		// create a priority queue
		HierarchicalFIFO Q = new HierarchicalFIFO( (int)min, (int)max);
		
		int ndim = labelMapMaxTree.numDimensions();
		long[] dimensions = new long[ndim]; labelMapMaxTree.dimensions(dimensions);
		
		// create a flat iterable cursor
		long[] minInt = new long[ ndim ], maxInt = new long[ ndim ];
		for ( int d = 0; d < ndim; ++d ){   minInt[ d ] = 0 ;    maxInt[ d ] = dimensions[d] - 1 ;  }
		FinalInterval interval = new FinalInterval( minInt, maxInt );
		final Cursor< IntType > input_cursor = Views.flatIterable( Views.interval( labelMapMaxTree, interval)).cursor();
		final Cursor< IntType > seed_cursor = Views.flatIterable( Views.interval( seed, interval)).cursor();
		
		// initialize tree and node features arrays
		double[] hCriteria = new double[2*nLeaves];
		double[] Imax = new double[2*nLeaves];
		int[] parent = new int[2*nLeaves];
		int[][] children = new int[2*nLeaves][];
		for(int i=0; i<hCriteria.length; i++)
		{
			children[i] = new int[] {-1,-1};
			parent[i]=i;
			hCriteria[i]=0;
			Imax[i]=min;
		}
		
		// fill the queue
		int idx=-1;
		int pixToProcessCount = 0;
		while( input_cursor.hasNext() )
		{
			++idx;
			IntType pInput = input_cursor.next();
			float pVal = pInput.getRealFloat();
			float valSeed = seed_cursor.next().getRealFloat(); 
			if ( pVal>=min)
			{
				pixToProcessCount++;
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
		IntType outOfBoundT = labelMapMaxTree.firstElement().createVariable(); 
		outOfBoundT.setReal(min-1);
		RandomAccess< IntType > input_XRA = Views.extendValue(labelMapMaxTree, outOfBoundT ).randomAccess();
		RandomAccess< IntType > input_XRA2 = input_XRA.copyRandomAccess();
		
		// define the connectivity
		long[][] neigh = ImageConnectivity.getConnectivityPos(ndim, connectivity.getConn() );
		int[] n_offset = ImageConnectivity.getIdxOffsetToCenterPix(neigh, dimensions);
		long[][] dPosList = ImageConnectivity.getSuccessiveMove(neigh);
		int nNeigh = n_offset.length;
		
		
		boolean[] isDequeued = new boolean[(int)labelMapMaxTree.size()];
		for (int i=0; i<isDequeued.length; i++)
			isDequeued[i]=false;
		
		/////////////////////////////////////////////////////////////////////////////////////
		// building the watershed and the tree //////////////////////////////////////////////
		ProgressDialog.setStatusText("HWatershed: building label map and segment tree");
		
		int newNode = nLeaves;
		int pixProcessed = 0;
		int prevPercentDone = 0;
		while( Q.HasNext() )
		{ 	
			
			pixProcessed++;
			final int percentDone = (pixProcessed*100)/pixToProcessCount;
			if( percentDone != prevPercentDone ){
				prevPercentDone = percentDone;
				ProgressDialog.setProgress( percentDone*0.01f );
				if (ProgressDialog.wasCancelled())
				{
					labelMapMaxTree=null;
					maxTree = null;
					ProgressDialog.reset();
					ProgressDialog.finish();
					wasCancelled=true;
					return;
				}
			}
			
			
			final int pIdx = (int) Q.Next(); 
			final int pVal = Q.getCurrent_level() + Q.getMin();
			
			final long[] posCurrent = new long[ndim];
			getPosFromIdx((long)pIdx, posCurrent, dimensions);
			input_XRA.setPosition(posCurrent);
			IntType p = input_XRA.get();
			int pLeaf = (int)(min - 1 - p.getRealFloat());
			int pNode = findRoot(pLeaf, parent);
			//System.out.println("pLeaf "+ pLeaf +" , pNode "+ pNode );
			isDequeued[pIdx]=true;
			
			// loop on neighbors			
			input_XRA2.setPosition(posCurrent);
			for( int i=0; i<nNeigh; i++)
			{
				final int nIdx = pIdx + n_offset[i];
				
				input_XRA2.move(dPosList[i]);
				final IntType n = input_XRA2.get();
				final float nVal = n.getRealFloat();
				
				if ( nVal != (min-1) ) // if n is in-bound
				{
					if( isDequeued[nIdx] ) // p is the lowest point 
					{	
						int nLeaf = (int)(min - 1 - nVal);
						int nNode = findRoot(nLeaf, parent);
						
						if( nNode != pNode ) // 2 distincts nodes are meeting and p is the saddle : merge Nodes
						{
							
							newNode++;
							double Hn = Imax[nNode]-pVal;
							hCriteria[nNode] = Hn;
							double Hp = Imax[pNode]-pVal;
							hCriteria[pNode] = Hp;

							//System.out.println("nLeaf "+ nLeaf +" , nNode "+ nNode +" , Hn "+ Hn );
							//System.out.println("pLeaf "+ pLeaf +" , pNode "+ pNode +" , Hp "+ Hp );

							//merge the node with smallest H with first neighbor node that has higher dynamics
							int node1, node2;
							double H1, H2;
							if (Hp == Hn){
								node1 = pNode;
								node2 = nNode;
							}
							else if ( Hp < Hn ){
								node1 = pNode;
								node2 = nLeaf;
								H1 = Hp;
								H2 = hCriteria[node2];
								while( H2 <= H1 )
								{	
									node2 = parent[node2];
									H2 = hCriteria[node2];
								}
								
								double HMerge;
								if( parent[node2]==node2 )
									HMerge = Double.POSITIVE_INFINITY;
								else
									HMerge = Math.min( hCriteria[children[parent[node2]][0]], hCriteria[children[parent[node2]][1]] );
								
								while( H1 > HMerge )
								{
									node2 = parent[node2];
									H2 = hCriteria[node2];
									if( parent[node2]==node2 )
										HMerge = Double.POSITIVE_INFINITY;
									else
										HMerge = Math.min( hCriteria[children[parent[node2]][0]], hCriteria[children[parent[node2]][1]] );
								}
							}
							else{ // if( Hn <= Hp )
								node1 = nNode;
								node2 = pLeaf;
								H1 = Hn;
								
								H2 = hCriteria[node2];
								while( H2 <= H1 )
								{	
									node2 = parent[node2];
									H2 = hCriteria[node2];
								}
								double HMerge;
								if( parent[node2]==node2 )
									HMerge = Double.POSITIVE_INFINITY;
								else
									HMerge = Math.min( hCriteria[children[parent[node2]][0]], hCriteria[children[parent[node2]][1]] );
								
								while( H1 > HMerge )
								{
									node2 = parent[node2];
									H2 = hCriteria[node2];
									if( parent[node2]==node2 )
										HMerge = Double.POSITIVE_INFINITY;
									else
										HMerge = Math.min( hCriteria[children[parent[node2]][0]], hCriteria[children[parent[node2]][1]] );
								}
							}
							mergeNodes(node1, node2, newNode, parent, children);
							pNode=findRoot(newNode, parent);
							//System.out.println("node1 "+ node1 +" , node2 "+ node2 +" , newNode "+ newNode +" , maxNode "+ (2*nLeaves) );

							Imax[newNode]= Math.max(Imax[node1], Imax[node2]);
							hCriteria[newNode] =  Math.max(hCriteria[node1], hCriteria[node2]); //Imax[newNode]-pVal;
							//
						}
					}
					
					if ( nVal>=min ) // is not queued yet and is in bound?
					{
						Q.add( nIdx, (int)nVal );
						n.setReal(min -1 - pLeaf);
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
		
		//////////////////////////////////////////////////////////////////////////////////
		// final pass on the label image /////////////////////////////////////////////////
		ProgressDialog.setStatusText("HWatershed: final pass");
		
		
		// convert the input to label image (label L is stored in input with value min-1-L all other value should be equal to min-1 )
		final IntType minT = labelMapMaxTree.firstElement().createVariable();
        minT.setReal(min-1);
        final IntType minusOneT = labelMapMaxTree.firstElement().createVariable();
        minusOneT.setReal(-1);
        Cursor<IntType> input_cursor2 = labelMapMaxTree.cursor();
        while( input_cursor2.hasNext() )
		{
        	IntType p = input_cursor2.next();
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
        
        ProgressDialog.finish();
        wasCancelled=false;
        return;
        // at the end, input was tranformed to a label image
        // hCriteria contains the dynamics of each peak
        // parent link nodes to their parent node, if label L has no parent, parent[L]=0 
        // these can be used to build any hMap on the fly.
	}
	
	
	protected static void mergeNodes(int node1, int node2, int newNode, int[] parent, int[][] children)
	{
		
		// update children of parent(node1) if needed
		int par1 = parent[node1];
		if( par1 != node1){ // node1 is not a root
			for(int i=0; i<2; i++){
				if ( children[par1][i] == node1  ){
					children[par1][i] = newNode;
					parent[newNode] = par1;
				}
			}
		}
							
		// update children of parent(node2) if needed
		int par2 = parent[node2];
		if( par2 != node2){ // node2 is not a root
			for(int i=0; i<2; i++){
				if ( children[par2][i] == node2  ){	
					children[par2][i] = newNode;
					parent[newNode] = par2;
				}
			}
		}
		parent[node1] = newNode;
		parent[node2] = newNode;
		children[newNode][0] = node1;
		children[newNode][1] = node2;
		
		return;
	}
	
	
	
	protected static int findRoot(int label, int[] parent)
	{
		// find the root of label 
		int labelParent = parent[label];
		while (labelParent!=label)  
		{
			label = labelParent;
			labelParent=parent[label];
		}
		return label;
	}
	
	
	protected static void getPosFromIdx(long idx, long[] position, long[] dimensions)
	{
		for ( int i = 0; i < dimensions.length; i++ )
		{
			position[ i ] = ( int ) ( idx % dimensions[ i ] );
			idx /= dimensions[ i ];
		}
	}
	
		
	

	
	public static void main(final String... args)
	{
		/*
		// image to flood
		new ij.ImageJ();
		//IJ.open("F:\\projects\\blobs.tif");
		IJ.open("F:\\projects\\2D_8peaks.tif");
		ImagePlus imp = IJ.getImage();
		//IJ.run(imp, "Gaussian Blur...", "sigma=2");
		ImagePlusImgConverter impImgConverter = new ImagePlusImgConverter(imp);
		Img<FloatType> input = impImgConverter.getImgFloatType();
		
		float threshold = Float.NEGATIVE_INFINITY;
		HWatershedLabeling<FloatType> maxTreeConstructor = new HWatershedLabeling<FloatType>(input, threshold, Connectivity.FULL);
		
		Img<IntType> output = maxTreeConstructor.getLabelMapMaxTree();
		ImagePlus imp_out = impImgConverter.getImagePlus(output);
		imp_out.show();
		
		//impImgConverter.getImagePlus( maxTreeConstructor.labelMapDebug ).show();
		
		int[] parents = maxTreeConstructor.getTree().getParentsAsArray();
		Map<Integer,Node> treeNodes = maxTreeConstructor.getTree().getNodes();
		double[] dynamics =  maxTreeConstructor.getTree().getFeature("dynamics");
		for( Node node : treeNodes.values())
		{
			int id = node.getId();
			int pId= id;
			if ( node.getParent()!= null )
				pId = node.getParent().getId();
			
			String str = "Id:"+id+"  ;  parent:"+pId+"  ;  children:";
			for(Node nodeC : node.getChildren() )
				str = str+nodeC.getId()+", ";
			str = str+"  ;  dyn:"+dynamics[id];
			System.out.println(str);
		}
		
		//System.out.println("Parents: " + Arrays.toString(parents));
		
		double[] attributes = maxTreeConstructor.getTree().getFeature("dynamics");
		System.out.println("Attributes: " + Arrays.toString(attributes));
		
		
		//maxTreeConstructor.getImgLabeling();
		
		//float[] attributes2 = new float[] {1, 2, 4, 8, 16, 32};
		float[] attributes2 = new float[] {0};
		//Arrays.copyOf(attributes, attributes.length);
		//Arrays.sort(attributes2);
		for( float val : attributes2 )
		{
			
			float h= val;
			Img<IntType> hMax = maxTreeConstructor.getHFlooding2(h);
			ImagePlus imp_hMax = impImgConverter.getImagePlus(hMax);
			imp_hMax.setTitle("hMax (h="+h+")");
			imp_hMax.show();
			IJ.run(imp_hMax, "3-3-2 RGB", "");
			IJ.setMinAndMax(imp_hMax, 0, parents.length);
			
		}
		
		*/
		
		
	}
	

}
