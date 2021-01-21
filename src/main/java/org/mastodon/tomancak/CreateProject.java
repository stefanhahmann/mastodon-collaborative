package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.command.Command;
import org.scijava.log.LogService;

import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.DatasetServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

@Plugin( type = Command.class, name = "Mastodon CreateRemoteProject space plugin" )
public class CreateProject implements Command
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;


	// ----------------- network options -----------------
	@Parameter(label = "URL address of the remote monitor:")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "New project name on the remote monitor:")
	private String projectName = "setHereProjectName";

	@Parameter(label = "Obfuscate the new project name:")
	private boolean secureURL = true;


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		try {
			remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
			final URL url = new URL(remoteMonitorURL
				+ (secureURL ?  "/addSecret/" : "/add/")
				+ projectName);

			//now connect and read out the status
			final String result = new BufferedReader(new InputStreamReader( url.openStream() )).readLine();
			if (!result.startsWith("ERROR"))
			{
				projectName = result;
				logService.info("The project was registered with this name: "+projectName);
				logService.info("Created a project space here: " + remoteMonitorURL + "/" + projectName);
			}
			else
			{
				logService.warn("Server refused to create the project! Nothing new registered.");
			}

		} catch (MalformedURLException | UnknownHostException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			logService.error("Some other IO error:"); e.printStackTrace();
		}
	}
}
