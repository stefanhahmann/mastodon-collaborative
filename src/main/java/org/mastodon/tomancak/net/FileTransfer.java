package org.mastodon.tomancak.net;

import java.io.OutputStream;
import java.net.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileTransfer
{
	static final String remoteURL = "http://localhost:"+FileServer.port;

	static final String filesRootFolder = "/temp/get";

	static public
	void getParticularFile(final String filename)
	{
		try {
			Files.copy(
				new URL(remoteURL + "/files/" + filename).openStream(),
				Paths.get(filesRootFolder + File.separator + filename)
			);
		} catch (FileNotFoundException e) {
			System.err.println("Server cannot serve this request:");
			e.printStackTrace();
		} catch (MalformedURLException e) {
			System.err.println("Cannot connect to the server:");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Cannot write the output file:");
			e.printStackTrace();
		}
	}

	static public
	void postParticularFile(final String filename, final int spotsCnt, final int linksCnt)
	{
		try {
			final URL url = new URL(remoteURL
			  + "/put"
			  + FileServer.fileUploadQueryStringCreate(URLEncoder.encode(filename,"UTF-8"),spotsCnt,linksCnt));
			//System.out.println("talking to: "+url);

			final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			Files.copy(
				Paths.get(filesRootFolder + File.separator + filename),
				conn.getOutputStream()
			);

			//this actually makes the whole thing spinning (does not send out a byte without this command)
			conn.getInputStream();
		} catch (MalformedURLException e) {
			System.err.println("Cannot connect to the server:");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Cannot write the output file:");
			e.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		//getParticularFile("displaySettings.txt");
		postParticularFile("FR.txt",10,20);
	}
}
