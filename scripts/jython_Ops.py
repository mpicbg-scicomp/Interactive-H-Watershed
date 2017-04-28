#@ImageJ ij

from ij import IJ

imp =IJ.getImage();
hMin = 500
thresh = 500
peakFlooding = 80
result = ij.op().run("HWatershed", imp, hMin, thresh, peakFlooding );
result.show()