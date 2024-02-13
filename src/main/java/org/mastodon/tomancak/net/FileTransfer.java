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

import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Files;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.mastodon.mamut.model.Model;

public class FileTransfer
{
	/** Makes sure the URL is with http:// started. Returns ref on the input
	    (no new String) if the input does not need to be fixed up. */
	public static
	String fixupURL(final String remoteURL)
	{
		if (remoteURL.startsWith("http://"))
			return remoteURL;
		else
			return "http://"+remoteURL;
	}


	public static
	Collection<String> listAvailableFiles(final String remoteURL)
	throws IOException
	{
		//we intentionally copy the incoming streamed content (not to keep a ref on it -> to allow it to close the connection)
		List<String> list = new LinkedList<>();
		new BufferedReader( new InputStreamReader( new URL(remoteURL + "/list/").openStream() ))
		      .lines()
		      .forEach(list::add);
		return list;
	}


	public static
	void getParticularFile(final String remoteURL, final String filename,
	                       final Path toThisLocalFolder)
	throws IOException
	{
		Files.copy(
			new URL(remoteURL + "/files/" + filename).openStream(),
			toThisLocalFolder.resolve(filename)
		);
	}


	public static
	void postParticularFile(final String remoteURL, final Model theModelItself,
	                        final String filenameWithTheModel,
	                        final Path fromThisLocalFolder)
	throws IOException
	{
		postParticularFile(remoteURL,
		                   filenameWithTheModel,
		                   theModelItself.getGraph().vertices().size(),
		                   theModelItself.getGraph().edges().size(),
		                   fromThisLocalFolder);
	}

	public static
	void postParticularFile(final String remoteURL, final String filename,
	                        final int spotsCnt, final int linksCnt,
	                        final Path fromThisLocalFolder)
	throws IOException
	{
		final URL url = new URL(remoteURL
		  + "/put"
		  + FileServer.fileUploadQueryStringCreate(URLEncoder.encode(filename,"UTF-8"),spotsCnt,linksCnt));
		//System.out.println("talking to: "+url);

		final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		Files.copy(
			fromThisLocalFolder.resolve(filename),
			conn.getOutputStream()
		);

		//this actually makes the whole thing spinning (does not send out a byte without this command)
		conn.getInputStream();
	}


	/* to demo some of the methods
	public static void main(String[] args)
	{
		try {

			getParticularFile("http://localhost:7070","2020-04-06__23:43:15__VladoDaTracker.mstdn","/temp");
			for (String file : listAvailableFiles("http://localhost:7070")) System.out.println(">>"+file+"<<");

		} catch (MalformedURLException e) {
			System.err.println("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			System.err.println("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Some IO error:"); e.printStackTrace();
		}
	}
	*/
}
