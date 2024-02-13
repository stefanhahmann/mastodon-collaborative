/*-
 * #%L
 * mastodon-collaborative
 * %%
 * Copyright (C) 2020 - 2024 Vladimir Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
