package de.mpicbg.scf.InteractiveWatershed_dev;

import de.mpicbg.scf.InteractiveWatershed_dev.imgTools.ImageConnectivity;

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


import ij.IJ;
import ij.ImagePlus;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.LanczosInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;


public class Utils {

	
	public static enum Interpolator{
		NearestNeighbor,
		Linear,
		Lanczos;
	}
	
	
	
	public static <T extends RealType<T> & NativeType<T>> Img<T> upsample(Img<T> input, float[] upsampling_factor, Interpolator interpType)
	{
		int nDim = input.numDimensions(); 
		long[] dims = new long[nDim];
		input.dimensions(dims);
		long[] out_size = new long[nDim];
		
		for(int i=0; i<nDim; i++)
			out_size[i] = (long) (  (float)dims[i] * upsampling_factor[i]  );
		
		return upsample( input, out_size, interpType);
	}
	
	
	
	public static <T extends RealType<T> & NativeType<T>> Img<T> upsample(Img<T> input, long[] out_size, Interpolator interpType){
		int nDim = input.numDimensions();
		if(nDim != out_size.length){
			return input;
		}
		long[] in_size = new long[nDim]; 
		input.dimensions(in_size);
		float[] upfactor = new float[nDim];
		for(int i=0; i<nDim; i++){
			upfactor[i] = (float)out_size[i]/in_size[i];
		}
		RealRandomAccess< T > interpolant;
		switch(interpType){
			case Linear:
				NLinearInterpolatorFactory<T> NLinterp_factory = new NLinearInterpolatorFactory<T>();
				interpolant = Views.interpolate( Views.extendBorder( input ), NLinterp_factory ).realRandomAccess();
				break;
			case Lanczos:
				LanczosInterpolatorFactory<T> LanczosInterp_factory = new LanczosInterpolatorFactory<T>();
				interpolant = Views.interpolate( Views.extendBorder( input ), LanczosInterp_factory ).realRandomAccess();
				break;
			default: // NearestNeighbor:
				NearestNeighborInterpolatorFactory<T> NNInterp_factory = new NearestNeighborInterpolatorFactory<T>();
				interpolant = Views.interpolate( Views.extendBorder( input ), NNInterp_factory ).realRandomAccess();
				break;
		}
		final ImgFactory< T > imgFactory = new ArrayImgFactory< T >();
		final Img< T > output = imgFactory.create( out_size , input.firstElement().createVariable() );
		Cursor< T > out_cursor = output.localizingCursor();
		float[] tmp = new float[2];
		while(out_cursor.hasNext()){
			out_cursor.fwd();
			for ( int d = 0; d < nDim; ++d )
				tmp[ d ] = out_cursor.getFloatPosition(d) /upfactor[d];
			interpolant.setPosition(tmp);
			out_cursor.get().setReal( Math.round( interpolant.get().getRealFloat() ) );
		}
		return output;
	}
	
	
	public static Img<? extends RealType<?>> wrapImagePlus( ImagePlus imp){
		
		Img<? extends RealType<?>> img = null;
		int type = imp.getType(); 
		
		switch( type ){
		case ImagePlus.GRAY8:
			img = ImageJFunctions.wrapByte(imp);
			break;
		case ImagePlus.GRAY16:
			img = ImageJFunctions.wrapShort(imp);
			break;
		case ImagePlus.GRAY32:
			img = ImageJFunctions.wrapFloat(imp);
			break;
		case ImagePlus.COLOR_256:
			IJ.error("image type ("+type+") not handled. "+imp.toString() );
			//img = ImageJFunctions.wrapRGBA(imp);			
			break;
		case ImagePlus.COLOR_RGB:
			IJ.error("image type ("+type+") not handled. "+imp.toString() );
			//img = ImageJFunctions.wrapRGBA(imp);			
			break;
		default:
			IJ.error("image type ("+type+") not handled. "+imp.toString() );
			break;
		}
		return img;
	}
	
	
	
	
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
	
	
	public static <T extends RealType<T> & NativeType<T>> Img<T> getLabelContour(Img<T> input, Connectivity connectivity)
	{
		int nDim = input.numDimensions();
		long[] dims = new long[nDim];
		input.dimensions(dims);
		Img<T> output = input.factory().create(dims, input.firstElement().createVariable() );
		
		//cursor on the extended input
		//long[] minInt = new long[ nDim ], maxInt = new long[ nDim ];
		//for ( int d = 0; d < nDim; ++d ){
		//	minInt[ d ] = 0 ;    
		//	maxInt[ d ] = dims[d] - 1 ;  
		//}
		//FinalInterval interval = new FinalInterval( minInt, maxInt );
		RandomAccess<T> raIn = Views.extendMirrorSingle(input).randomAccess();
		
		// random accessible on the ouput
		RandomAccess<T> raOut = output.randomAccess();
		
		// define the connectivity
		long[][] neigh = ImageConnectivity.getConnectivityPos(nDim, connectivity.getConn() );
		int[] n_offset = ImageConnectivity.getIdxOffsetToCenterPix(neigh, dims);
		long[][] dPosList = ImageConnectivity.getSuccessiveMove(neigh);
		int nNeigh = n_offset.length;
		
		// browse through the image
		for(long idx=0; idx<input.size(); idx++){
			
			final long[] pos = new long[nDim];
			getPosFromIdx( idx, pos, dims);
			raIn.setPosition(pos);
			float pVal = raIn.get().getRealFloat();
			
			if( pVal > 0 ){
				
				// loop on neighbors			
				for( int i =0; i<nNeigh; i++){
					
					raIn.move(dPosList[i]);
					float nVal = raIn.get().getRealFloat();
					
					// if n different from p then update ouput value
					if ( nVal != pVal ) {
						raOut.setPosition(pos);
						raOut.get().setReal(pVal);
						break;
					}
				}
				
			}
		}
		return output;
	}
	
	
	
	/**
	 *  Build an image compatible with particle analyzer (8-connected binary region )
	 * @param input is a label image
	 * @return is a binary image
	 */
	public static <T extends RealType<T> & NativeType<T>> Img<IntType> getPARegions(RandomAccessibleInterval<T> input)
	{
		int nDim = input.numDimensions();
		long[] dims = new long[nDim];
		input.dimensions(dims);
		ImgFactory<IntType> imgFactory = Util.getArrayOrCellImgFactory( input ,  new IntType(0) );
		Img<IntType> output = imgFactory.create(  input, new IntType()  );
				
		
		Connectivity connectivity = Connectivity.FULL; 
		RandomAccess<T> raIn = Views.extendMirrorSingle(input).randomAccess();
		
		// random accessible on the ouput
		RandomAccess<IntType> raOut = output.randomAccess();
		
		// define the connectivity
		long[][] neigh = ImageConnectivity.getConnectivityPos(nDim, connectivity.getConn() );
		int[] n_offset = ImageConnectivity.getIdxOffsetToCenterPix(neigh, dims);
		long[][] dPosList = ImageConnectivity.getSuccessiveMove(neigh);
		int nNeigh = n_offset.length;
		
		long nElements = Intervals.numElements( input );
		
		// browse through the image
		for(long idx=0; idx<nElements; idx++){
			
			final long[] pos = new long[nDim];
			getPosFromIdx( idx, pos, dims);
			raIn.setPosition(pos);
			float pVal = raIn.get().getRealFloat();
			
			if( pVal > 0 ){
				
				// loop on neighbors
				raOut.setPosition(pos);
				raOut.get().setInteger(255);
			
				for( int i =0; i<nNeigh; i++){
					
					raIn.move(dPosList[i]);
					float nVal = raIn.get().getRealFloat();
					
					// if p is strictly inferior to n then p it is a border between two label, its value is set to 0
					if ( pVal < nVal ) {
						raOut.get().setInteger( 0 );
						break;
					}
				}
				
			}
		}
		return output;
	}
	
	
	protected static void getPosFromIdx(long idx, long[] position, long[] dimensions)
	{
		for ( int i = 0; i < dimensions.length; i++ )
		{
			position[ i ] = ( int ) ( idx % dimensions[ i ] );
			idx /= dimensions[ i ];
		}
	}
	
}
