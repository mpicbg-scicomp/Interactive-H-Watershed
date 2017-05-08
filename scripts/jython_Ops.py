# @ImagePlus imp
# @OpService ops
# @OUTPUT ImagePlus result

hMin = 500
thresh = 500
peakFlooding = 80
result = ops.run("HWatershed", imp, hMin, thresh, peakFlooding );
