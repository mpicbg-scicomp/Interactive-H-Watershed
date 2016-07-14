package de.mpicbg.scf.InteractiveMaxTree;

import java.util.ArrayList;
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
	boolean[] nodeSelection;
	
	
	public TreeLabeling( Tree tree)
	{
		this.tree = tree;
		nodeIdToLabel = new int[tree.numNodes];
	}
	
	
	public int[] getNodeIdToLabel_LUT(boolean[] nodeSelection, Rule rule){
		
		for(int node=0; node<nodeIdToLabel.length; node++)
			nodeIdToLabel[node]=-1;
		
		this.nodeSelection = nodeSelection;
		
		switch( rule ){
		case MAX :
			maxRuleLabeling(nodeSelection);
			break;
		default :
			break;
		}
		
		return nodeIdToLabel;
	}
	
	private void maxRuleLabeling(boolean[] nodeSelection)
	{
		for( Node node : tree.getRoots() )
			if( nodeSelection[node.getId()] )
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
			List<Boolean> tests = new ArrayList<Boolean>();
			for(Node child : children)
				tests.add( nodeSelection[child.getId()] );
			
			boolean noChildSelected = children.stream().allMatch( child -> !nodeSelection[child.getId()] );
			if( noChildSelected ) // if no children are selected  then we can label the node
			{
				nodeIdToLabel[id]=id;
				labelOffsprings(node, id);	
			}
			else
			{
				for(Node child : children)
					if( nodeSelection[child.getId()] )
						analyzeNode_MaxRule(child);
			}			
		}	
	}
	
	
	// assumes node is already labeled
	protected void labelOffsprings(Node node, int label)
	{
		if( !Tree.isLeaf(node) )	
			for( Node child : node.getChildren() )
			{
				int childId = child.getId();
				nodeIdToLabel[childId]=label;
				labelOffsprings(child,label);
			}
		return;
	}
	
	
}
