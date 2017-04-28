package de.mpicbg.scf.InteractiveWatershed;


import java.util.ArrayList;
import java.util.LinkedList;

public class HierarchicalFIFO {
	
	private int current_level;
	public int getCurrent_level() {
		return current_level;
	}


	public int getMin() {
		return min;
	}

	private final int min;
	//private final int max;
	private int max_level;
	private ArrayList<LinkedList<Integer>> QueueList; 
	
	
	public HierarchicalFIFO(int min, int max)
	{
		int nbin = max - min + 1;
		QueueList = new ArrayList<LinkedList<Integer>>(nbin);
		for(int i=0; i<nbin; i++)
			QueueList.add( new LinkedList<Integer>() );
		this.min = min;
		//this.max = max;
		this.max_level = nbin-1;
		current_level = max_level;
	}
	
	
	public void add(long idx, int val)
	{
		int level = val - min ;
		QueueList.get(level).add( (int) idx  );
		current_level = Math.max(current_level,level); // would crash if level>max_level
	}
	
	public boolean HasNext()
	{
		while( QueueList.get(current_level).isEmpty() & current_level>0)
			current_level--;
		
		return !QueueList.get(current_level).isEmpty();
	}
	
	public long Next()
	{	
		return QueueList.get(current_level).poll();	
	}


}