package org.mastodon.tomancak.net;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedList;

/** Collection of triggers relevant to ONE particular dataset */
public class DatasetListeners
{
	// --------------- actions interfaces ---------------
	/** notifies when a lineage file was uploaded,
	    and reports its parameters */
	public interface LineageArrived
	{
		void action(final LocalDateTime date, final String user,
		            final int noOfSpots, final int noOfLinks);
	}

	/** notifies when a file was uploaded */
	public interface FileArrived
	{
		void action(final String filename);
	}

	/** notifies when existing file was requested for download */
	public interface FileRequested
	{
		void action(final String filename);
	}


	// --------------- management of server (that is, over all datasets) listeners ---------------
	private final String datasetName;
	private final ServerListeners serverListeners;

	public DatasetListeners(final String datasetName, final ServerListeners serverListeners)
	{
		this.datasetName     = datasetName;
		this.serverListeners = serverListeners;
	}

	public DatasetListeners(final String datasetName)
	{
		this(datasetName,null);
	}


	// --------------- management of dataset-specific listeners ---------------
	final Collection<LineageArrived> lineageArrivedListeners = new LinkedList<>();
	final Collection<FileArrived>    fileArrivedListeners    = new LinkedList<>();
	final Collection<FileRequested>  fileRequestedListeners  = new LinkedList<>();


	public void addLineageArrivedListeners(final LineageArrived listener)
	{ lineageArrivedListeners.add( listener ); }

	public void removeLineageArrivedListeners(final LineageArrived listener)
	{ lineageArrivedListeners.remove( listener ); }


	public void addFileArrivedListeners(final FileArrived listener)
	{ fileArrivedListeners.add( listener ); }

	public void removeFileArrivedListeners(final FileArrived listener)
	{ fileArrivedListeners.remove( listener ); }


	public void addFileRequestedListeners(final FileRequested listener)
	{ fileRequestedListeners.add( listener ); }

	public void removeFileRequestedListeners(final FileRequested listener)
	{ fileRequestedListeners.remove( listener ); }


	/** helper method to notify all relevant listeners */
	void notifyLineageArrivedListeners(final LocalDateTime date, final String user,
	                                   final int noOfSpots, final int noOfLinks)
	{
		new Thread(() -> {
			lineageArrivedListeners.forEach( a -> a.action(date,user,noOfSpots,noOfLinks) );
			if (serverListeners != null)
				serverListeners.lineageArrivedListeners.forEach( a -> a.action(datasetName,date,user,noOfSpots,noOfLinks) );
		}).start();
	}

	/** helper method to notify all relevant listeners */
	void notifyFileArrivedListeners(final String filename)
	{
		new Thread(() -> {
			fileArrivedListeners.forEach( a -> a.action(filename) );
			if (serverListeners != null)
				serverListeners.fileArrivedListeners.forEach( a -> a.action(datasetName,filename) );
		}).start();
	}

	/** helper method to notify all relevant listeners */
	void notifyFileRequestedListeners(final String filename)
	{
		new Thread(() -> {
			fileRequestedListeners.forEach( a -> a.action(filename) );
			if (serverListeners != null)
				serverListeners.fileRequestedListeners.forEach( a -> a.action(datasetName,filename) );
		}).start();
	}
}
