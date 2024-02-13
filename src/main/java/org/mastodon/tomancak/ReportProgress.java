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

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;

import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.model.Model;
import org.mastodon.tomancak.util.LineageFiles;
import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.DatasetServer;

import java.io.IOException;
import java.nio.file.Path;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

@Plugin( type = Command.class, name = "Mastodon ReportProgress plugin" )
public class ReportProgress
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter
	private PrefService prefService;

	@Parameter(persist = false)
	private ProjectModel appModel;

	// ----------------- user name and file name -----------------
	@Parameter(label = "Identify yourself as:",
		description = "Please, choose your nickname using only common characters -- this will be part of an output filename.",
		initializer = "initLineageFile", callback = "updateLineageFile")
	private String userName = System.getProperty("user.name");

	@Parameter(label = "Going to write file:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false)
	private String lineageFullFilenameStr; //printed representation of the Path just below
	private  Path  lineageFullFilename;

	//cannot be initialized when the object is created because the 'appModel' would not be yet available
	private  Path  projectRootFoldername;

	//a file name (without the path) as a derivative of the current 'userName'
	private String lineageFilename;

	private
	void initLineageFile()
	{
		projectRootFoldername = LineageFiles.getProjectRootFoldername(appModel);

		//try to retrieve the 'lineageFilename' from the preferences
		//but only if there is actually some name already stored!
		final String newUserName = prefService.get(ReportProgress.class,"userName");
		if (newUserName != null) userName = newUserName;

		//finally, set up the 'lineageFilename'
		updateLineageFile();
	}

	private
	void updateLineageFile()
	{
		lineageFilename = LineageFiles.lineageFilename(userName);
		lineageFullFilename = projectRootFoldername.resolve(lineageFilename);
		lineageFullFilenameStr = lineageFullFilename.toString();
	}

	// ----------------- network options -----------------
	@Parameter(label = "Report also to a remote monitor:",
		description = "Request that the current progress shall be saved also on a remote host.")
	private boolean sendAlsoToRemoteMonitor = false;

	@Parameter(label = "URL address of the remote monitor:",
		description = "This entry is ignored if the above is not checked.",
		persistKey = "remoteMonitorURL")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "Project name on the remote monitor:",
		description = "This entry is ignored if the above is not checked.",
		persistKey = "projectName")
	private String projectName = "setHereProjectName";


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		//bail out if we are started incorrectly...
		if (appModel == null) return;

		//LoadEarlierProgress reads 'remoteMonitorURL' itsway... so we have to save thatway too
		prefService.put(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",sendAlsoToRemoteMonitor);
		prefService.put(LoadEarlierProgress.class,"remoteMonitorURL",remoteMonitorURL);
		prefService.put(LoadEarlierProgress.class,"projectName",projectName);

		//test if the file can be created at all -- main worry is about
		//the content of the 'userName' part
		try {
			lineageFullFilename.toFile().createNewFile();
		} catch (IOException e) {
			logService.error("Cannot create lineage file: "+ lineageFullFilename);
			e.printStackTrace();
			return;
		}

		//ok, create-able, let's export data then
		final Model model = appModel.getModel();
		try {
			LineageFiles.saveModelIntoLineageFile(model, lineageFullFilename);
			logService.info("Saved: "+lineageFullFilename);

			if (sendAlsoToRemoteMonitor)
			{
				remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
				final String URL = remoteMonitorURL + "/" + projectName;
				logService.info("Saving also to remote URL: "+URL);
				FileTransfer.postParticularFile(URL, model, lineageFilename, projectRootFoldername);
			}
		} catch (MalformedURLException | UnknownHostException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
			return;
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
			return;
		} catch (IOException e) {
			logService.error("Failed saving the lineage file!"); e.printStackTrace();
			return;
		}

		logService.info("Saved lineage with "+model.getGraph().vertices().size()
			+ " vertices and " + model.getGraph().edges().size() +" edges");
	}
}
