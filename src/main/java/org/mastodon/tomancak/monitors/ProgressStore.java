package org.mastodon.tomancak.monitors;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;

import org.mastodon.tomancak.net.DatasetServer;
import org.mastodon.tomancak.net.DatasetListeners;
import org.mastodon.tomancak.net.ServerListeners;

public class ProgressStore
implements ServerListeners.LineageArrived, DatasetListeners.LineageArrived
{
	/** our contract is to store the progress for this given dataset */
	public
	ProgressStore(final String dataset)
	{
		this.dataset = dataset;
	}

	final String dataset;

	public static
	ProgressStore createAttachedProgressStore(final Path datasetFolder,
	                                          final DatasetServer server,
	                                          final boolean enableGnuplotPngStats,
	                                          final boolean enableHtmlTableStats)
	{
		final ProgressStore ps = new ProgressStore(datasetFolder.getFileName().toString());
		if (enableGnuplotPngStats) ps.gnuplotSetupDirs(datasetFolder);
		if (enableHtmlTableStats) ps.htmlOutputFile = datasetFolder.resolve("status.html");
		ps.attachToThisServer(server);
		return ps;
	}


	// --------------------- connection with DatasetServers ---------------------
	/** monitor the given server for changes on "our" dataset,
	    note that one may monitor multiple servers... */
	public
	void attachToThisServer(final DatasetServer ds)
	{
		final ServerListeners hookOnTheseListeners = ds.listeners;
		if (hookOnTheseListeners == null) //sanity check...
			throw new RuntimeException("Given server is in very bad shape! Bailing out...");

		//try to hook ourselves on the specific dataset handler -- if it exists already,
		//if it does not, hook on the general level (and filter incoming actions later)
		DatasetListeners dsL = hookOnTheseListeners.getDatasetListeners(dataset);
		if (dsL != null)
		{
			//hooking onto the specific list of listeners
			dsL.addLineageArrivedListeners( this );
		}
		else
		{
			//hooking onto the list of general listeners
			hookOnTheseListeners.addLineageArrivedListeners( this );
		}

		//now "replay" what everything the sever has witnessed so far
		ds.replayLineageArrivedOnDataset(this, dataset);
	}

	/** monitor the given server for changes on "our" dataset,
	    note that one may monitor multiple servers... */
	public
	void detachFromThisServer(final DatasetServer ds)
	{
		final ServerListeners unhookFromTheseListeners = ds.listeners;
		if (unhookFromTheseListeners == null) //sanity check...
			throw new RuntimeException("Given server is in very bad shape! Bailing out...");

		//try to unhook ourselves from the specific dataset handler -- if it exists at all
		DatasetListeners dsL = unhookFromTheseListeners.getDatasetListeners(dataset);
		if (dsL != null)
		{
			//unhooking from the specific list of listeners
			dsL.removeLineageArrivedListeners( this );
		}

		//also, unhooking from the list of general listeners
		unhookFromTheseListeners.removeLineageArrivedListeners( this );
	}


	// --------------------- actions handlers ---------------------
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
			if (htmlOutputFile != null) htmlProcessor.writeHtmlTableFile(htmlOutputFile);
		} catch (IOException e) {
			System.out.println("Some problem writing files:");
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
		BufferedWriter osw = Files.newBufferedWriter(gnuplotOutputFolder.resolve(userName+".dat"));
		for (long time : userStats.keySet())
		{
			long progress = userStats.get(time);
			osw.write(LocalDateTime.ofEpochSecond(time,0,ZoneOffset.UTC).toString()
					+"\t"+ time +"\t"+ progress +"\n");
		}
		osw.close();

		//run gnuplot now, the .gnuplot file content is "encrypted" below
		Runtime.getRuntime().exec("gnuplot refreshPlot.gnuplot", new String[0], gnuplotOutputFolder.toFile());
	}

	public
	void gnuplotSetupDirs(final Path datasetFolder)
	{
		//define the working folder
		gnuplotOutputFolder = datasetFolder.resolve("gnuplot");

		//create the 'gnuplot' sub-folder
		final File subFolder = gnuplotOutputFolder.toFile();
		if (!subFolder.isDirectory() && !subFolder.mkdir())
			throw new RuntimeException("Cannot create folder: "+subFolder);

		//place Script in here
		final Path sp = gnuplotOutputFolder.resolve("refreshPlot.gnuplot");
		try {
			BufferedWriter script = Files.newBufferedWriter(sp);
			script.write("# run me as \"gnuplot refreshPlot.gnuplot\" in this folder"); script.newLine();
			script.newLine();
			script.write("set terminal png size 800,800"); script.newLine();
			script.write("set output \"../status.png\""); script.newLine();
			script.write("set size 0.87, 0.93"); script.newLine();
			script.newLine();
			script.write("files=system('ls *.dat')"); script.newLine();
			script.write("set ylabel \"progress (spots+links)\""); script.newLine();
			script.write("unset xtics"); script.newLine();
			script.write("set x2tics"); script.newLine();
			script.write("set x2tics rotate by 45"); script.newLine();
			script.write("set x2label \"time\""); script.newLine();
			script.write("set grid x2tics"); script.newLine();
			script.write("set grid ytics"); script.newLine();
			script.write("set key left top"); script.newLine();
			script.write("plot for [D in files] D u 2:3:x2ticlabels(1) w lp t D ps 2 noenhanced"); script.newLine();
			script.close();
		} catch (IOException e) {
			System.out.println("Problem writing file "+sp);
			e.printStackTrace();
			gnuplotOutputFolder = null;
		}
	}


	// --------------------- HTML outputs (via logs-processor) ---------------------
	/** if not null, HTML outputs will be made here */
	public Path htmlOutputFile = null;
	private final LogsProcessor htmlProcessor = new LogsProcessor(this.stats);
}
