# Interactive-H-Watershed
Interactive-H-Watershed is a plugin for the image analysis software ImageJ that let end user explore
possible watershed segmentation interactively on the fly. Interactivity is made possible by not rerunning
the watershed for every parameters but rather by recombining the segments after a tree structure and then
lazilly relabelling the part of the image visualized.

## To compile the project:
Simply clone the repository and load it in your IDE as a maven project to compile it

## To try the plugin:
In imageJ add the SCF-MPI-CBG update site. Open the image to segment and then click the menu entry SCF>Interactive H_Watershed

For more details information on the plugin usage and principlesgo to the [plugin wiki page](http://imagej.net/Interactive_Watershed) 
