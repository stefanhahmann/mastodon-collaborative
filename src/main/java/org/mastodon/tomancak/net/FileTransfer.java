package org.mastodon.tomancak.net;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileTransfer
{
	static final String remoteURL = "http://localhost:7070";

	static public
	void getParticularFile(final String filename)
	{
		final String localRootFolder = "/temp";

		try {
			Files.copy(
				new URL(remoteURL + "/" + filename).openStream(),
				Paths.get(localRootFolder + File.separator + filename)
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

	public static void main(String[] args)
	{
		getParticularFile("google.txt");
	}
}
