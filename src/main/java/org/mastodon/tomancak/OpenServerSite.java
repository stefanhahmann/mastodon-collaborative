package org.mastodon.tomancak;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.mastodon.tomancak.net.DatasetServer;
import org.mastodon.tomancak.util.OpenOsWebBrowser;

@Plugin( type = Command.class, name = "Mastodon ShowServer plugin" )
public class OpenServerSite implements Command
{
	@Parameter
	private LogService logService;

	@Parameter(label = "URL address of the remote monitor:", persistKey = "remoteMonitorURL")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "Project name on the remote monitor:", persistKey = "projectName")
	private String projectName = "setHereProjectName";


	@Override
	public void run()
	{
		final String URL = remoteMonitorURL + "/" + projectName +"/files/";
		logService.info("Opening in web browser the URL: "+URL);
		OpenOsWebBrowser.openUrl(URL);
	}
}