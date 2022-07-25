package org.mastodon.tomancak;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.mastodon.tomancak.util.OpenOsWebBrowser;

/**
 * This class is intentionally designed as a scijava command to make this command
 * reachable also within the scijava/Fiji ecosystem but from outside Java.
 */
 @Plugin( type = Command.class, name = "Mastodon ReportProgress plugin" )
public class OpenDocsSite implements Command
{
	@Parameter
	private LogService logService;

	@Override
	public void run()
	{
		final String URL = "https://github.com/mastodon-sc/mastodon-collaborative#what-is-mastodon-collaborative";
		logService.info("Opening in web browser the URL: "+URL);
		OpenOsWebBrowser.openUrl(URL);
	}
}