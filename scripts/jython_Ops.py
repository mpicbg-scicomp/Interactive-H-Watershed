# @ImagePlus imp
# @OpService ops
# @UIService ui

# The parameters "Seed Dynamics", "Intensity threshold" and "peak flooding" can be made optional

hMin = 0
thresh = 100
peakFlooding = 50

result0 = ops.run("H_Watershed", imp)

result1 = ops.run("H_Watershed", imp, hMin)

result2 = ops.run("H_Watershed", imp, hMin, thresh)

#result3 = ops.run("H_Watershed", imp, None, thresh)

#result4 = ops.run("H_Watershed", imp, None, None, peakFlooding)

result5 = ops.run("H_Watershed", imp, hMin, thresh, peakFlooding )

outputMask = True
result6 = ops.run("H_Watershed", imp, hMin, thresh, peakFlooding, outputMask )



ui.show(result0)
ui.show(result1)
ui.show(result2)
#ui.show(result3)
#ui.show(result4)
ui.show(result5)
ui.show(result6)

