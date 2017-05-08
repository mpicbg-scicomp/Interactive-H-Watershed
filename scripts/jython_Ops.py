# @ImagePlus imp
# @OpService ops
# @OUTPUT ImagePlus result

hMin = 20
thresh = 100
peakFlooding = 90
result = ops.run("H_Watershed", imp, hMin, thresh, peakFlooding )


