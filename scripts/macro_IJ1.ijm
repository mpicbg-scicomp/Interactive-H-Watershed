
hMin = 20;
thresh = 100;
peakFlooding = 90;
run("H_Watershed", "impin=["+getTitle()+"] hmin="+hMin+" thresh="+thresh+" peakflooding="+peakFlooding + " outputmask=true allowsplitting=false");