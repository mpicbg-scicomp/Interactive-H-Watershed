package de.mpicbg.scf.InteractiveMaxTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tree {
	
	Map<Integer, Node> nodes;
	List<Node> roots;
	List<Node> leaves;
	int numNodes=0;
	HashMap<String, double[]> features;
	
	
	boolean updated=false;
	
	/**
	 * build an empty tree
	 */
	public Tree(){
		this.numNodes = 0;
		this.nodes = new HashMap<Integer, Node>();
		this.roots = new ArrayList<Node>();
		this.leaves = new ArrayList<Node>();
		this.features = new HashMap<String,double[]>();
	}
	

	

	/**
	 * Adhoc constructor for the tree build by MaxTreeConstruction class
	 * @param parent, parent of node i is given by parent[i], root point to themselves
	 * @param children, children of node i is given by children[i], leaves point to -1
	 */
	public Tree( int[] parent, int[][] children){
		
		this.numNodes = parent.length;
		this.nodes = new HashMap<Integer, Node>(); 
		this.roots = new ArrayList<Node>();
		this.leaves = new ArrayList<Node>();
		this.features = new HashMap<String,double[]>();
		
		for( int i=0; i<numNodes; i++)
			nodes.put(i, new Node(i) );
		
		for( int i=0; i<numNodes; i++)
		{
			Node node = nodes.get(i);
			int nodeParent = parent[i];
			if( nodeParent != i )
				node.setParent( nodes.get(nodeParent) );
			
			List<Node> childrenList = new ArrayList<Node>();
			for( int c : children[i] )
				if( c>=0 )
					childrenList.add(nodes.get(c));
			node.setChildren(childrenList);
		}
		
		update();
		
	}
	
	
	
	public static boolean isLeaf(Node node)
	{
		return node.getChildren().isEmpty();
	}
	
	
	public static boolean isRoot(Node node)
	{
		return (node.getParent()==null);
	}
	
	
	protected void update()
	{
		if( updated )
			return;
		
		for(Node node : nodes.values() )
		{
			if( isRoot(node) )
				roots.add(node);
			if( isLeaf(node) )
				leaves.add(node);
		}
		updated=true;
	}
	
	
	
	public Map<Integer, Node> getNodes() {
		return nodes;
	}


	public List<Node> getRoots() {
		update();
		return roots;
	}


	public List<Node> getLeaves() {
		update();
		return leaves;
	}


	public int getNumNodes() {
		return numNodes;
	}
	
	
	public HashMap<String,double[]> getFeatures() { 
		return features; 
	}
	
	
	public double[] getFeature(String feat) {
		return features.get(feat);
	}
	
	
	public void setFeature(String feat, double[] value) {
		features.put(feat, value);
	}
	
	
	public int[] getParentsAsArray()
	{
		int[] parent = new int[this.numNodes];
		for(Node node : nodes.values() )
		{	
			int id = node.getId();
			if( Tree.isRoot(node) )
			{
				parent[id] = id;
			}
			else
			{
				parent[id] = node.getParent().getId();
			}
		}
			
		return parent;
	}
	
	
	protected class Node
	{
		Integer id;
		Node parent;
		List<Node> children;
		
		public Node(int id)
		{
			this.id = id;
			this.parent = null;
			this.children = new ArrayList<Node>();
		}
		
		public Node getParent() {
			return parent;
		}
		
		public void setParent(Node parent) {
			this.parent = parent;
		}
		
		public Integer getId() {
			return id;
		}
		
		public List<Node> getChildren() {
			return children;
		}
		
		public void setChildren(List<Node> children) {
			this.children = children;
		}
	}
	

}
