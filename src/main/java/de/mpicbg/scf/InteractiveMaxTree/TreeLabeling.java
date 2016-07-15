package de.mpicbg.scf.InteractiveMaxTree;

import java.util.LinkedList;
import java.util.List;

import de.mpicbg.scf.InteractiveMaxTree.Tree.Node;

public class TreeLabeling {
	
	
	public enum Rule{
		MIN("min"),
		MAX("max"),
		DIRECT("direct"),
		Subtractive("subtractive");
		
		private String name;
		Rule(String name){
			this.name = name;
		}
		
		public String getName() {
			return name;
		}	
	}
	
	Tree tree;
	int[] nodeIdToLabel;
	
	
	public TreeLabeling( Tree tree)
	{
		this.tree = tree;
		this.nodeIdToLabel = new int[tree.numNodes];
	}
	
	
	public int[] getNodeIdToLabel_LUT(boolean[] nodeSelection, Rule rule){
		
		for(int node=0; node<nodeIdToLabel.length; node++)
			nodeIdToLabel[node]=0;
		
		// initialize nodes flag and decoration 
		for(Node node : tree.getNodes().values())
		{
			node.setFlag( nodeSelection[node.getId()] );
			node.setDecoration(-1);
		}
		
		switch( rule ){
		case MAX :
			DepthFirstExploration_MaxRule();
			break;
		default :
			break;
		}
		
		
		return nodeIdToLabel;
	}
	
	/*
	private void maxRuleLabeling()
	{
		for( Node node : tree.getRoots() )
			if( node.getFlag() )
				analyzeNode_MaxRule(node);
	}
	
	protected void analyzeNode_MaxRule(Node node)
	{
		int id = node.getId();
		if( Tree.isLeaf(node) )
		{
			nodeIdToLabel[id]=id;// label
		}
		else{
			List<Node> children = node.getChildren();
			
			boolean noChildSelected = children.stream().allMatch( child -> !child.getFlag() );
			if( noChildSelected ) // if no children are selected  then we can label the node
			{
				nodeIdToLabel[id]=id;
				labelOffsprings(node, id);	
			}
			else
			{
				for(Node child : children)
					if( child.getFlag() )
						analyzeNode_MaxRule(child);
			}			
		}	
	}
	
	
	// assumes node is already labeled
	protected void labelOffsprings(Node node, int label)
	{
		if( !Tree.isLeaf(node) )	// might well work without any test
			for( Node child : node.getChildren() )
			{
				int childId = child.getId();
				nodeIdToLabel[childId]=label;
				labelOffsprings(child,label);
			}
		return;
	}
	
	*/
	
	protected void DepthFirstExploration_MaxRule(){
		
		LinkedList<Node> Q_toExplore = new LinkedList<Node>();
		LinkedList<Node> Q_toLabel = new LinkedList<Node>();
		
		for( Node node : tree.getRoots() )
		{
			Q_toExplore.add(node);
		}
		
		// find the node to label
		while( ! Q_toExplore.isEmpty() )
		{
			final Node node = Q_toExplore.poll();
			List<Node> children = node.getChildren();
			boolean noChildMeetCriteria = true;
			for( Node child : children)
			{
				if( child.getFlag() )
				{	
					Q_toExplore.add(child);
					noChildMeetCriteria = false;
				}
			}
			if( noChildMeetCriteria ) // 1st node for whom no descendant meets the criteria will be labeled (as well as all his descendant)
			{
				Q_toLabel.add(node);
			}
		}
		
		// label the element of Q_toLabel and their offsprings.  
		while( !Q_toLabel.isEmpty() )
		{
			final Node node = Q_toLabel.poll();
			final int id = node.getId();
			
			if( node.getDecoration()==-1)
				node.setDecoration(id);
			
			float deco = node.getDecoration(); 
			for( Node child : node.getChildren() )
			{	
				child.setDecoration(deco);
				Q_toLabel.add(child);
			}
		}
		
		for( Node node : tree.getNodes().values() )
		{
			if( node.getDecoration()>=0 )
				nodeIdToLabel[node.getId()] = (int) node.getDecoration();
			// else the nodeIdToLabel[node.getId()] is already set to 0
		}
		
	}
	
	
}
