package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;

import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.tomancak.util.LineageFiles;
import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.FileServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.net.ConnectException;
import java.net.MalformedURLException;

@Plugin( type = Command.class, name = "Mastodon LoadEarlierProgress plugin" )
public class LoadEarlierProgress
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MastodonPluginAppModel appModel;

	// ----------------- local folder -----------------
	@Parameter(label = "Searching in this folder:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false,
		initializer = "initialSetupHelper")
	private String projectRootFoldername;
	//NB: cannot be initialized when the object is created because the 'appModel' would not be yet available

	private
	boolean isCheckBoxSavedInPreferences(final String attribName, final PrefService ps)
	{
		boolean returnedDefault = ps.getBoolean(LoadEarlierProgress.class, attribName, false) == false;
		returnedDefault &= ps.getBoolean(LoadEarlierProgress.class, attribName, true) == true;
		return !returnedDefault;
	}

	private
	void initialSetupHelper()
	{
		//this one cannot be setup at object creation time (appModel was null back then)
		projectRootFoldername = LineageFiles.getProjectRootFoldername(appModel);

		//PrefService is activated only after all initializers are over (because only
		//then the CommandService sees what's left to be initialized), but we need this value
		//already during the discoverInputFiles() initializer...
		//
		//try to retrieve the 'readAlsoFromRemoteMonitor' from the preferences
		final PrefService ps = logService.getContext().getService(PrefService.class);
		if (ps != null)
		{
			//but read it only if there is actually some name already stored!
			final String newRemoteURL = ps.get(LoadEarlierProgress.class,"remoteMonitorURL");
			if (newRemoteURL != null) remoteMonitorURL = newRemoteURL;

			if (isCheckBoxSavedInPreferences("readAlsoFromRemoteMonitor", ps))
				readAlsoFromRemoteMonitor = ps.getBoolean(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",false);
		}

		discoverInputFiles();
	}

	// ----------------- network options -----------------
	@Parameter(label = "Read also from a remote monitor:",
		description = "Request that the progress files shall be retrieved also from a remote host.",
		callback = "discoverInputFiles")
	private boolean readAlsoFromRemoteMonitor = false;

	@Parameter(label = "URL address of the remote monitor:",
		description = "This entry is ignored if the above is not checked.")
	private String remoteMonitorURL = "setHereServerAddress:"+ FileServer.defaultPort;


	// ----------------- available file names -----------------
	@Parameter(label = "Choose from the detected files:", persist = false, choices = {})
	private String lineageFilename = "none available yet";

	private
	void discoverInputFiles()
	{
		//read choices
		try {
			final ArrayList<String> choices = new ArrayList<>(50);

			//local files
			logService.info("Reading from "+projectRootFoldername);
			LineageFiles.listLineageFiles(projectRootFoldername)
			  .forEach(p -> { choices.add("Local: " + p.getFileName().toString()); });

			//remote files
			if (readAlsoFromRemoteMonitor)
			{
				remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
				logService.info("Reading from "+remoteMonitorURL);
				FileTransfer.listAvailableFiles(remoteMonitorURL)
				  .forEach(p -> { choices.add("Remote: " + p); });
			}

			//choices.forEach(s -> System.out.println(">>"+s+"<<"));

			//make sure every button toggle is remembered
			final PrefService ps = logService.getContext().getService(PrefService.class);
			if (ps != null)
				ps.put(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",readAlsoFromRemoteMonitor);

			getInfo().getMutableInput("lineageFilename", String.class).setChoices( choices );
		} catch (MalformedURLException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			logService.error("Failed listing the folder: "+projectRootFoldername);
			e.printStackTrace();
		}
	}


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		//bail out if we are started incorrectly, or on wrong input file...
		if (appModel == null) return;

		//fixup the filename and test it for validity
		boolean doRemoteRead = false;
		if (lineageFilename.startsWith("Local:"))
			lineageFilename = lineageFilename.substring(7);
		else if (lineageFilename.startsWith("Remote:"))
		{
			lineageFilename = lineageFilename.substring(8);
			doRemoteRead = true;
		}
		if (!LineageFiles.lineageFilePattern.test(lineageFilename)) return;

		/*
		//create new Model with the same params:
		final Model refModel = appModel.getAppModel().getModel();
		final Model newModel = LineageFiles.createEmptyModelWithUnitsOf(refModel);
		*/

		//let's replace the main graph
		final Model model = appModel.getAppModel().getModel();
		LineageFiles.startImportingModel(model);
		//
		try {
			if (doRemoteRead) {
				logService.info("Loading from remote URL: " + remoteMonitorURL);
				FileTransfer.getParticularFile(remoteMonitorURL, lineageFilename, projectRootFoldername);
				//files arrives to the local folder....
			}

			final String lineageFullFilename = projectRootFoldername + File.separator + lineageFilename;
			logService.info("Loading: " + lineageFullFilename);
			LineageFiles.loadLineageFileIntoModel(lineageFullFilename, model);
		} catch (MalformedURLException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			logService.error("Failed loading the lineage file!");
			e.printStackTrace();
		}
		//
		LineageFiles.finishImportingModel(model);

		logService.info("Replaced with lineage with "+model.getGraph().vertices().size()
			+ " vertices and " + model.getGraph().edges().size() +" edges");
	}
}
