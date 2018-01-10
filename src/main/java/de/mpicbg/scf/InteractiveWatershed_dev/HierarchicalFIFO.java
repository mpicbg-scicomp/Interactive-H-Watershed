package de.mpicbg.scf.InteractiveWatershed_dev;


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