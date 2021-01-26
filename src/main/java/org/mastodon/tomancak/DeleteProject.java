package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;

import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.DatasetServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

@Plugin( type = Command.class, name = "Mastodon DeleteRemoteProject space plugin" )
public class DeleteProject implements Command
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter
	private PrefService prefService;


	// ----------------- network options -----------------
	@Parameter(label = "URL address of the remote monitor:",
		persistKey = "remoteMonitorURL")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "Delete this project on the remote monitor:",
		persistKey = "projectName")
	private String projectName = "setHereProjectName";


	// ----------------- AreYouSure form -----------------
	@Parameter(label = "Are you sure (deletion cannot be reverted):", persist = false)
	private boolean areYouSure = false;


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		if (!areYouSure)
		{
			logService.info("User is not sure... bailing out.");
			return;
		}

		//LoadEarlierProgress reads 'remoteMonitorURL' itsway... so we have to save thatway too
		prefService.put(LoadEarlierProgress.class,"remoteMonitorURL",remoteMonitorURL);
		prefService.put(LoadEarlierProgress.class,"projectName",projectName);

		try {
			remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
			final URL url = new URL(remoteMonitorURL + "/remove/" + projectName);

			//now connect and read out the status
			final String result = new BufferedReader(new InputStreamReader( url.openStream() )).readLine();
			if (result.startsWith("OK"))
			{
				logService.info("Removed a project space here: " + remoteMonitorURL + "/" + projectName);
			}
			else
			{
				logService.warn("Server refused to remove the project!");
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
