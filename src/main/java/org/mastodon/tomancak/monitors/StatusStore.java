package org.mastodon.tomancak.monitors;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;

import org.mastodon.tomancak.net.DatasetListeners;
import org.mastodon.tomancak.net.ServerListeners;

public class StatusStore
implements ServerListeners.LineageArrived, DatasetListeners.LineageArrived
{
    /** monitor the given server for changes on the given dataset */
	public
	StatusStore(final ServerListeners listeners, final String dataset)
	{
		this.dataset = dataset;

		//try to hook ourselves on the specific dataset handler -- if it exists already,
		//if it does not, hook on the general level (and filter incoming actions later)
		DatasetListeners dsL = listeners.getDatasetListeners(dataset);
		if (dsL != null)
		{
			//hooking onto the specific list of listeners
			dsL.addLineageArrivedListeners( this );
		}
		else
		{
			//hooking onto the list of general listeners
			listeners.addLineageArrivedListeners( this );
		}
	}

	final String dataset;

	@Override
	public
	void action(final String dataset, final LocalDateTime date, final String user,
	            final int noOfSpots, final int noOfLinks)
	{
		if (this.dataset.equals(dataset))
			action(date,user,noOfSpots,noOfLinks);
	}

	@Override
	public
	void action(final LocalDateTime date, final String user,
	            final int noOfSpots, final int noOfLinks)
	{
		Map<Long,Long> userStats = stats.get(user);
		if (userStats == null)
		{
			//first time adding this user's data
			userStats = new TreeMap<>();
			stats.put(user,userStats);
		}
		userStats.put(date.toEpochSecond(ZoneOffset.UTC), (long)(noOfSpots+noOfLinks));

		//TODO: add more outputs
		try {
			if (gnuplotOutputFolder != null) gnuplotWriteFile(user,userStats);
		} catch (IOException e) {
			System.out.println("Some problem writing GNUplot files:");
			e.printStackTrace();
		}
	}

	/** maps Users to their Times-to-Progress maps */
	final Map<String, Map<Long,Long>> stats = new HashMap<>(10);


	// --------------------- gnuplot outputs ---------------------
	/** if not null, gnuplot outputs will be made */
	public Path gnuplotOutputFolder = null;

	public
	void gnuplotWriteFile(final String userName, final Map<Long,Long> userStats)
	throws IOException
	{
		OutputStreamWriter osw = new OutputStreamWriter(new BufferedOutputStream(
				new FileOutputStream( gnuplotOutputFolder.resolve(userName+".dat").toFile() )));

		for (long time : userStats.keySet())
		{
			long progress = userStats.get(time);
			osw.write(LocalDateTime.ofEpochSecond(time,0,ZoneOffset.UTC).toString()
					+"\t"+ time +"\t"+ progress +"\n");
		}

		osw.close();

		//run gnuplot now
		Runtime.getRuntime().exec("gnuplot refreshPlot.gnuplot", new String[0], gnuplotOutputFolder.toFile());

		/* this is the content of the refreshPlot.gnuplot
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
		*/
	}
}
