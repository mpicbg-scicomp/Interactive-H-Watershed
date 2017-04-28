
from ij import IJ

hMin = 500;
thresh = 500;
peakFlooding = 80;
IJ.run("HWatershed", "impin=2DEmbryoSection_Mette.tif hmin="+str(hMin)+" thresh="+str(thresh)+" peakflooding="+str(peakFlooding));