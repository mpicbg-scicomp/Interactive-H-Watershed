from ij import IJ

imp = IJ.getImage()
hMin = 20;
thresh = 100;
peakFlooding = 90;


IJ.run("H_Watershed", "impin="+imp.getTitle()+" hmin="+str(hMin)+" thresh="+str(thresh)+" peakflooding="+str(peakFlooding)+" outputmask=true allowsplitting=true" );