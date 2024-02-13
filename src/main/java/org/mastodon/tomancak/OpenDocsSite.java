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
package org.mastodon.tomancak;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.widget.FileWidget;
import org.mastodon.tomancak.net.DatasetServer;
import org.mastodon.tomancak.util.OpenOsWebBrowser;

import java.io.File;
import java.nio.file.Paths;

/**
 * This class is intentionally designed as a scijava command to make this command
 * reachable also within the scijava/Fiji ecosystem but from outside Java.
 */
@Plugin( type = Command.class, name = "Mastodon ShowDocs plugin" )
public class OpenDocsSite implements Command
{
	@Parameter
	private LogService logService;
	@Parameter
	private CommandService cmdService;

	private static final String OPTION_MAIN_WEB = "The official documentation web site (in a web browser)";
	private static final String OPTION_DOCS_SERVER = "Documentation how to start the collaborative server";
	private static final String OPTION_DOCS_BDS = "Documentation how to start the image data server (BigDataServer)";
	private static final String OPTION_CLI_CMD = "Command to start collaborative server from command line (Terminal), shown in Fiji->Window->Console window";

	@Parameter(label = "I want to see: ", choices = {
			OPTION_MAIN_WEB,
			OPTION_DOCS_SERVER,
			OPTION_DOCS_BDS,
			OPTION_CLI_CMD
	})
	private String whatToShow;

	@Override
	public void run()
	{
		String URL = null;
		switch (whatToShow) {
			case OPTION_MAIN_WEB:
				URL = "https://github.com/mastodon-sc/mastodon-collaborative#what-is-mastodon-collaborative";
				break;
			case OPTION_DOCS_SERVER:
				URL = "https://github.com/mastodon-sc/mastodon-collaborative/blob/master/SERVER.md#the-server";
				break;
			case OPTION_DOCS_BDS:
				URL = "https://github.com/mastodon-sc/mastodon-collaborative/blob/master/BDS.md#how-to-start-bigdataserver-bds";
				break;
			case OPTION_CLI_CMD:
				cmdService.run(PrepareCliCmd.class,true);
		}

		if (URL != null) {
			logService.info("Opening in web browser the URL: "+URL);
			OpenOsWebBrowser.openUrl(URL);
		}
	}


	@Plugin( type = Command.class, name = "Mastodon ShowCliCmd plugin" )
	public static class PrepareCliCmd implements Command {
		@Parameter
		private LogService logService;

		@Parameter(label = "Where to store server data:", style = FileWidget.DIRECTORY_STYLE)
		private File dataDir;

		@Parameter(label = "What IP address the server uses:")
		private String serverIP = "localhost";

		@Parameter(label = "What TCP port the server uses:")
		private int serverPort = DatasetServer.defaultPort;

		@Override
		public void run() {
			//JAVA -cp "FIJI/jars/*" org.mastodon.tomancak.net.DatasetServer
			String patternMsg = "General pattern: java -cp \"/path/to/Fiji.app/jars/*\""
					+ " org.mastodon.tomancak.net.DatasetServer"
					+ " fullPathToLocalDirToStoreProjects"
					+ " [hostIP [hostPort]]";
			String serverStartMsg = "USE THIS ONE: java -cp \""
					+ Paths.get( logService.getLocation() ).getParent().toString()
					+ File.separator + "*\" org.mastodon.tomancak.net.DatasetServer  "
					+ dataDir.getAbsolutePath() + "  " + serverIP + " " + serverPort;

			logService.info(patternMsg);
			logService.info("... where [abc] means optional parameter of value abc.");
			logService.warn(serverStartMsg);
		}
	}
}
