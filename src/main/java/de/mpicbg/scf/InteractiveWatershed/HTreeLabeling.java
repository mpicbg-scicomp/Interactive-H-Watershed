package de.mpicbg.scf.InteractiveWatershed;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import de.mpicbg.scf.InteractiveWatershed.Tree.Node;

public class HTreeLabeling {
	
	boolean initialized = false;
	
	Tree tree;
	int nNodes;
	double[] dyn;
	double[] Imax;
	double[] Imin;
	double[] criteria;
	
	public HTreeLabeling(Tree tree)
	{
		this.tree = tree;
		initialize();
	}

	private void initialize()
	{
		this.dyn = tree.getFeature("dynamics");
		this.Imax = tree.getFeature("Imax");

		this.nNodes = Imax.length;
		this.Imin = new double[ nNodes ];
		for( int i=0; i<nNodes; i++) {
			Imin[i] = Imax[i]-dyn[i];
		}
		
		this.criteria = new double[nNodes];
		for( Node node : tree.getNodes().values() )
		{
			if( Tree.isLeaf(node) )
				criteria[node.getId()] = 0;
			else {
				final int c0 = node.getChildren().get(0).getId();
				final int c1 = node.getChildren().get(1).getId();
				criteria[node.getId()] = Math.min( dyn[c0], dyn[c1]);
			}
		}
		
		Queue<Node> Q = new LinkedList<Node>();
		Q.addAll( tree.getRoots() );
		double epsilon = 0.000000001;
		while( ! Q.isEmpty() ) {
			Node node = Q.poll();
			if( ! Tree.isLeaf(node) ) {
				final Node c0 = node.getChildren().get(0);
				final Node c1 = node.getChildren().get(1);
				final int c0Id = c0.getId();
				final int c1Id = c1.getId();
				if(Imax[c0Id]==Imax[c1Id])
					Imax[c1Id] -= epsilon;
				Q.add(c0);
				Q.add(c1);	
			}
		}
		
		
		initialized = true;
	}
	
	
	public int getLabeling( double hMin, double threshold, double peakFlooding, boolean keepOrphanPeak, int[] nodeIdToLabel, int[] nodeIdToLabelRoot, double[] thresholds )
	{
		
		peakFlooding = Math.max(0, peakFlooding);
		peakFlooding = Math.min(100, peakFlooding);
		peakFlooding = peakFlooding/100d;
		
		// initialize nodes local threshold 
		for( int i=0; i<nNodes; i++) {
			thresholds[i] = threshold + ( Imax[i]-threshold ) * ( 1-peakFlooding );
		}
		
		
		// initialize the tree exploration by inserting roots in a Queue
		Queue<Node> queue = new LinkedList<Node>();
		for(Node node : tree.getRoots()){
			node.labelRoot = node.getId();
			queue.add(node);
			
		}
		
		// breadth first search till we find the root of the cut tree (all nodes above the cut value don't mater anymore)
		//	 - a node i is active if crit[i]<hMin and Imax[i]>threshold
		 
		int label=0;
		Queue<Node> queueA = new LinkedList<Node>();
		while( ! queue.isEmpty() )
		{
			final Node node = queue.poll();
			final int nodeId = node.getId();
			if( criteria[nodeId] <= hMin ) {
				if( Imax[nodeId] >= threshold ) {
					//currentLabel++;
					//node.finalLabel = currentLabel; 
					node.labelRoot = nodeId;
					label++;
					queueA.add(node);
				}
				else {
					node.labelRoot = 0;
				}
			}
			else {
				node.labelRoot = 0;
				for(Node childNode : node.getChildren() )
					queue.add( childNode );
			}
		}
		
	
		
		
		while( ! queueA.isEmpty() )
		{
			final Node node = queueA.poll();
			
			int nodeId = node.getId();
			double Imin_node = Imin[nodeId];
			
			double Imax_node = Imax[nodeId];
			if ( threshold>Imax_node ) {
				node.labelRoot = 0;
			}
			else if( threshold >= Imin_node) {
				node.labelRoot = nodeId;
				label++;
			}
			else {
				if(thresholds[node.labelRoot] > Imin_node  && Imax[node.labelRoot]>Imax[nodeId]) {
					if( keepOrphanPeak ){
						node.labelRoot = nodeId;
						label++;
					}
					else {
						node.labelRoot = 0;
					}
				}
			}
			//else { // do nothing }
			
			for( Node child : node.getChildren() )
			{
				child.labelRoot = node.labelRoot;
				queueA.add(child);
			}
			
		}
		
		
		
		
		// from the tree labeling determine label continuously filling the range [1, nLabel]
		int currentLabel = 0;
		for( Node node : tree.getNodes().values() )
		{
				final int nodeId = node.getId();
				if ( nodeId == node.labelRoot && node.labelRoot>0) {
					currentLabel++;
					nodeIdToLabel[nodeId] = currentLabel;
					nodeIdToLabelRoot[nodeId] = node.labelRoot;
				}
		}
		
		for( Node node : tree.getNodes().values() )
		{
				final int nodeId = node.getId();
				nodeIdToLabel[nodeId] = nodeIdToLabel[node.labelRoot];
				nodeIdToLabelRoot[nodeId] = node.labelRoot;
		}
		nodeIdToLabel[0] = 0; // just to be sure
		nodeIdToLabelRoot[0] = 0;
		

		
		return label;
	}
	
	// threshold correction 
	//	T_node = T + (Imax_node-T).(1-a)
	// there is an a, a_, such that T_root_a_ = Imax_node - h_node
	// we can deduce a T_ such that T_node_a_ = T_root_a_
	// the function return T_node_ = T_ + (Imax_node - T_) . (1-a) 
	private static double correctedThreshold(double Imax, double ImaxR, double a_, double T, double a)
	{	
		double T_ = T + (ImaxR-Imax)* ( 1 - a_ ) / a_ ;
		double Tnode_corr = T_*a + Imax * (1-a) ;
		return Tnode_corr;
	}
	
	
}
