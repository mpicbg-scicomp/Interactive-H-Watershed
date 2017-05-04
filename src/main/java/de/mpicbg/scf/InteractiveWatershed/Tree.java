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
		boolean flag= false;
		float decoration=0;
		int labelRoot = 0;
		
		public float getDecoration() {
			return decoration;
		}

		public void setDecoration(float decoration) {
			this.decoration = decoration;
		}

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
		
		public boolean getFlag() {
			return flag;
		}

		public void setFlag(boolean flag) {
			this.flag = flag;
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
