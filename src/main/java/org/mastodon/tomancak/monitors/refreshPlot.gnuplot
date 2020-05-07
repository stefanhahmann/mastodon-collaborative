# run me as "gnuplot refreshPlot.gnuplot" in this folder

set terminal png size 800,800
set output "status.png"

files=system('ls *.dat')
set ylabel "progress (spots+links)"
unset xtics
set x2tics
set x2tics rotate by 45
set x2label "time"
plot for [D in files] D u 2:3:x2ticlabels(1) w lp t D ps 2
