package org.mastodon.tomancak.net;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

/** Collection of triggers relevant to the whole server */
public class ServerListeners
{
	// --------------- actions interfaces ---------------
	/** notifies when a lineage file was uploaded,
	    and reports its parameters */
	public interface LineageArrived
	{
		void action(final String dataset, final LocalDateTime date, final String user,
		            final int noOfSpots, final int noOfLinks);
	}

	/** notifies when a file was uploaded */
	public interface FileArrived
	{
		void action(final String dataset, final String filename);
	}

	/** notifies when existing file was requested for download */
	public interface FileRequested
	{
		void action(final String dataset, final String filename);
	}

	//TODO: if need arises, we can have a security monitoring listeners
	//such as: MismatchedDatasetRequested, RemoveNonExistingDatasetRequested,
	//NonExistingFileRequested, or maintanance monitoring listeners
	//such as FailedDuringDatasetRemoval


	// --------------- management of server listeners (for all current datasets) ---------------
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


	// --------------- management of dataset-specific listeners ---------------
	final Map<String, DatasetListeners> datasetListeners = new HashMap<>(10);

	/** returns reference on a DatasetListeners that is specific for the given dataset
	    so that caller can manage listeners specifically for it, it may however
	    return null if no such dataset is known to the server */
	public DatasetListeners getDatasetListeners(final String forThisDataset)
	{ return datasetListeners.get( forThisDataset ); }
}
