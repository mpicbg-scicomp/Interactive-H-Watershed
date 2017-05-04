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


import java.util.LinkedList;
import java.util.List;
import de.mpicbg.scf.InteractiveWatershed.Tree.Node;

public class TreeUtils {

	
	static public int[] getTreeLabeling(Tree tree, String featureName, double cut){
		boolean makeNewLabels = false;
		return getTreeLabeling(tree, featureName, cut, makeNewLabels);
	}

	
	/**
	 * Return a node labeling corresponding to a merging of all the nodes below the feature cut value  
	 * @param tree the tree to label
	 * @param featureName name of the tree feature on which the tree will be labeled
	 * @param cut any node below that value is label similar to its parent
	 * @param makeNewLabels if true new labels from 1 to the number of CCs below the cut
	 * @return an array matching tree nodes to a label
	 */
	static public int[] getTreeLabeling(Tree tree, String featureName, double cut, boolean makeNewLabels){
		
		
		int[] nodeIdToLabel;
		double[] feature = tree.getFeature(featureName);
		
		if(feature==null){
			nodeIdToLabel = new int[tree.numNodes];
			for(int node=0; node<nodeIdToLabel.length; node++){
				nodeIdToLabel[node]=0;
			}
		}
		else{
			LinkedList<Node> labelSeeds = getLabelRoots(tree, feature, cut );
			
			// set a label for each node
			if( makeNewLabels ){
				int label = 1;
				for(Node node : labelSeeds){
					node.setDecoration( label );
					node.labelRoot = node.getId();
					label++;
				}
			}
			else{ // we the node ID as a label
				for(Node node : labelSeeds){
					node.setDecoration( node.getId() );
					node.labelRoot = node.getId();
				}
			}
			
			nodeIdToLabel = labelFromSeeds(tree, labelSeeds);
		}
		
		return nodeIdToLabel;
	}
	
	
	/**
	 * Helper function findings the most root node of each label 
	 * i.e. right above the cut (and with not parent right above the cut value)
	 * the approach assumes node feature is decreasing from root to leafs   
	 * @param tree
	 * @param feature node attributes on which the tree cut is determined
	 * @param cut any node below that value is merged with its parent
	 * @return return a list of the most root node for each label right above the cut ()the most root nodes with a child below the cut
	 */
	protected static LinkedList<Node> getLabelRoots(Tree tree, double[] feature, double cut ){
		
		// initialize nodes flag and decoration 
		for(Node node : tree.getNodes().values())
		{
			node.setFlag( feature[node.getId()]>cut );
			node.setDecoration(-1);
		}
		
		LinkedList<Node> Q_toExplore = new LinkedList<Node>();
		LinkedList<Node> Q_toLabel = new LinkedList<Node>();
		
		for( Node node : tree.getRoots() )
		{
			if (node.getFlag())
				Q_toExplore.add(node);
		}
		
		// find the node to label
		while( ! Q_toExplore.isEmpty() )
		{
			final Node node = Q_toExplore.poll();
			
			
			List<Node> children = node.getChildren();
			if ( children.size()==0 )
			{
				Q_toLabel.add(node);
				continue;
			}
			
			boolean allChildMeetCriteria=true;
			for( Node child : children)
			{
				if ( !child.getFlag() )
				{
					allChildMeetCriteria=false;
					Q_toLabel.add(node);
					break;
				}
			}
			
			if( allChildMeetCriteria )
				for( Node child : children)
					Q_toExplore.add(child);
			
		}
		
		return Q_toLabel;
	}
	
	
	/**
	 * Label all the nodes below the seed according to seed ID	
	 * @param tree
	 * @param labelSeeds list of most root node for each label
	 * @return an array matching node Id to a label
	 */
	protected static int[] labelFromSeeds(Tree tree, LinkedList<Node> labelSeeds){
		// label the element of Q_toLabel and their offsprings.  
		while( !labelSeeds.isEmpty() ){
			final Node node = labelSeeds.poll();
			for( Node child : node.getChildren() ){	
				child.setDecoration( node.getDecoration() );
				child.labelRoot = node.labelRoot;
				labelSeeds.add(child);
			}
		}
		
		int[] nodeIdToLabel = new int[tree.getNumNodes()];
		for( Node node : tree.getNodes().values() )
		{
			if( node.getDecoration()>=0 )
				nodeIdToLabel[node.getId()] = (int) node.getDecoration();
			// else 0 by initialization		
		}
		return nodeIdToLabel;
	}
	
	
}
