package de.mpicbg.scf.InteractiveWatershed;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
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
	
	
	public static Img<?> wrapImagePlus( ImagePlus imp){
		
		Img<?> img = null;
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
			img = ImageJFunctions.wrapRGBA(imp);			
			break;
		case ImagePlus.COLOR_RGB:
			img = ImageJFunctions.wrapRGBA(imp);			
			break;
		default:
			IJ.error("image type ("+type+") not handled. "+imp.toString() );
			break;
		}
		return img;
	}
	
	
}
