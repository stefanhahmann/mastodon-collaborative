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
	}

	/** maps Users to their Times-to-Progress maps */
	final Map<String, Map<Long,Long>> stats = new HashMap<>(10);
}
