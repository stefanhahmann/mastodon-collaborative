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
import org.mastodon.tomancak.merging.MergeDatasets;
import org.mastodon.tomancak.merging.MergeModels;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

@Plugin( type = Command.class, name = "Mastodon LoadEarlierProgress plugin" )
public class LoadEarlierProgress
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter
	private PrefService prefService;

	@Parameter(persist = false)
	private MastodonPluginAppModel appModel;

	// ----------------- local folder -----------------
	@Parameter(label = "Searching in this folder:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false,
		initializer = "initialSetupHelper")
	private String projectRootFoldername;
	//NB: cannot be initialized when the object is created because the 'appModel' would not be yet available

	private
	boolean isCheckBoxSavedInPreferences(final String attribName)
	{
		boolean returnedDefault = prefService.getBoolean(LoadEarlierProgress.class, attribName, false) == false;
		returnedDefault &= prefService.getBoolean(LoadEarlierProgress.class, attribName, true) == true;
		return !returnedDefault;
	}

	private
	void initialSetupHelper()
	{
		//this one cannot be setup at object creation time (appModel was null back then)
		projectRootFoldername = LineageFiles.getProjectRootFoldername(appModel);

		//PrefService is activated only after all initializers are over (because only
		//then the CommandService sees what's left to be initialized), but we need some
		//values already during the discoverInputFiles() initializer...
		//
		//try to retrieve the 'readAlsoFromRemoteMonitor' from the preferences
		//but only if there is actually some URL already stored!
		if (isCheckBoxSavedInPreferences("readAlsoFromRemoteMonitor"))
			readAlsoFromRemoteMonitor = prefService.getBoolean(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",false);

		final String newRemoteURL = prefService.get(LoadEarlierProgress.class,"remoteMonitorURL");
		if (newRemoteURL != null) remoteMonitorURL = newRemoteURL;

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
	void enlistNewInputFile(final List<String> localOnlyFiles,
	                        final List<String> syncedFiles,
	                        final List<String> remoteOnlyFiles,
	                        final String newRemoteFile)
	{
		if (localOnlyFiles.contains(newRemoteFile))
		{
			//_move_ the already known file (from localOnly to Synced)
			localOnlyFiles.remove(newRemoteFile);
			syncedFiles.add(newRemoteFile);
		}
		else
		{
			//_create_ a new known file (in remoteOnly)
			remoteOnlyFiles.add(newRemoteFile);
		}
	}

	private
	void discoverInputFiles()
	{
		//read choices
		try {
			//first, make sure every button toggle is remembered
			prefService.put(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",readAlsoFromRemoteMonitor);
			prefService.put(LoadEarlierProgress.class,"remoteMonitorURL",remoteMonitorURL);

			//second, start populating the list of discovered input files
			final List<String> localOnlyFiles  = new LinkedList<>();
			final List<String> syncedFiles     = new LinkedList<>();
			final List<String> remoteOnlyFiles = new LinkedList<>();

			//local files
			logService.info("Reading from "+projectRootFoldername);
			LineageFiles.listLineageFiles(projectRootFoldername)
			  .forEach(p -> { localOnlyFiles.add(p.getFileName().toString()); });

			//remote files
			if (readAlsoFromRemoteMonitor)
			{
				remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
				logService.info("Reading from "+remoteMonitorURL);
				FileTransfer.listAvailableFiles(remoteMonitorURL)
				  .forEach(p -> { enlistNewInputFile(localOnlyFiles,syncedFiles,remoteOnlyFiles,p); });
			}

			//merge (and prefix) the known input files into a single list
			final List<String> choices = new ArrayList<>(localOnlyFiles.size()+syncedFiles.size()+remoteOnlyFiles.size());
			localOnlyFiles.forEach(  f -> choices.add("Local only : "+f));
			syncedFiles.forEach(     f -> choices.add("Synced     : "+f));
			remoteOnlyFiles.forEach( f -> choices.add("Remote only: "+f));
			//choices.forEach(s -> System.out.println(">>"+s+"<<"));

			getInfo().getMutableInput("lineageFilename", String.class).setChoices( choices );
		} catch (MalformedURLException | UnknownHostException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
		} catch (IOException e) {
			logService.error("Failed listing the folder: "+projectRootFoldername);
			e.printStackTrace();
		}
	}

	@Parameter(label = "What to do with the loaded file:",
		choices = {"Replace the current lineage", "Merge with the current lineage"})
	private String actionWithNewFile;


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		//bail out if we are started incorrectly, or on wrong input file...
		if (appModel == null) return;

		final boolean doRemoteRead = lineageFilename.startsWith("Remote");

		//fixup the filename and test it for validity
		lineageFilename = lineageFilename.substring(13);
		if (!LineageFiles.lineageFilePattern.test(lineageFilename)) return;

		//reference on an existing/old and a new model that shall be filled from the file
		final Model refModel = appModel.getAppModel().getModel();
		final Model newModel
			= actionWithNewFile.startsWith("Replace") ? refModel  //fill directly into the actual lineage
			: LineageFiles.createEmptyModelWithUnitsOf(refModel); //create an extra lineage

		//let's read the new graph
		LineageFiles.startImportingModel(newModel);
		//
		try {
			if (doRemoteRead) {
				logService.info("Loading from remote URL: " + remoteMonitorURL);
				FileTransfer.getParticularFile(remoteMonitorURL, lineageFilename, projectRootFoldername);
				//files arrives to the local folder....
			}

			final String lineageFullFilename = projectRootFoldername + File.separator + lineageFilename;
			logService.info("Loading: " + lineageFullFilename);
			LineageFiles.loadLineageFileIntoModel(lineageFullFilename, newModel);
		} catch (MalformedURLException | UnknownHostException e) {
			logService.error("URL is probably wrong:"); e.printStackTrace();
			return;
		} catch (ConnectException e) {
			logService.error("Some connection error:"); e.printStackTrace();
			return;
		} catch (IOException e) {
			logService.error("Failed loading the lineage file!"); e.printStackTrace();
			return;
		}

		if (actionWithNewFile.startsWith("Replace"))
		{
			//replace... just report of what's been done
			logService.info("Replaced with lineage with "+newModel.getGraph().vertices().size()
				+ " vertices and " + newModel.getGraph().edges().size() +" edges.");
		}
		else
		{
			//merge... but first: need to mine some params!
			final double distCutoff = 1000;
			final double mahalanobisDistCutoff = 1;
			final double ratioThreshold = 2;

			//create an extra copy (duplicate) of the current lineage,
			//the current lineage 'refModel' will be filled with the merged data
			//
			//duplicate by exporting & importing -- which we sell as backup before the merge...
			final Model copyModel = LineageFiles.createEmptyModelWithUnitsOf(refModel);
			try {
				final String auxBackupFile = projectRootFoldername + File.separator + "backupCurrentLineageBeforeMerge.mstdn";
				LineageFiles.saveModelIntoLineageFile(refModel, auxBackupFile);
				LineageFiles.loadLineageFileIntoModel(auxBackupFile, copyModel);
			} catch (IOException e) {
				logService.error("Failed backing up the lineage file!");
				e.printStackTrace();
				return;
			}

			final MergeDatasets.OutputDataSet output = new MergeDatasets.OutputDataSet( refModel );
			MergeModels.merge( copyModel, newModel, output, distCutoff, mahalanobisDistCutoff, ratioThreshold );

			logService.info("Merged the current lineage (A) with "+copyModel.getGraph().vertices().size()
					+ " vertices and " + copyModel.getGraph().edges().size() +" edges together");
			logService.info("  with the loaded lineage (B) with "+newModel.getGraph().vertices().size()
					+ " vertices and " + newModel.getGraph().edges().size() +" edges");
			logService.info("  to create a new lineage with "+refModel.getGraph().vertices().size()
					+ " vertices and " + refModel.getGraph().edges().size() +" edges.");
		}
		//
		LineageFiles.finishImportingModel(newModel);
	}
}
