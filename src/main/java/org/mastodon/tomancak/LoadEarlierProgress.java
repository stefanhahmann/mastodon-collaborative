package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.command.CommandService;

import java.nio.file.Path;

import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.tomancak.merging.MergeDatasets;
import org.mastodon.mamut.tomancak.merging.MergeModels;
import org.mastodon.tomancak.util.MergeModelDialog;
import org.mastodon.tomancak.util.LineageFiles;
import org.mastodon.tomancak.net.FileTransfer;
import org.mastodon.tomancak.net.DatasetServer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
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
	private MamutPluginAppModel appModel;

	// ----------------- local folder -----------------
	@Parameter(label = "Searching in this folder:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false,
		initializer = "initialSetupHelper")
	private String projectRootFoldernameStr; //printed representation of the Path just below
	private  Path  projectRootFoldername;
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
		projectRootFoldernameStr = projectRootFoldername.toString();

		//PrefService is activated only after all initializers are over (because only
		//then the CommandService sees what's left to be initialized), but we need some
		//values already during the discoverInputFiles() initializer...
		//
		//try to retrieve the 'readAlsoFromRemoteMonitor' from the preferences
		//but only if there is actually some URL already stored!
		if (isCheckBoxSavedInPreferences("readAlsoFromRemoteMonitor"))
			readAlsoFromRemoteMonitor = prefService.getBoolean(LoadEarlierProgress.class,"readAlsoFromRemoteMonitor",false);

		String helperURL = prefService.get(LoadEarlierProgress.class,"remoteMonitorURL");
		if (helperURL != null) remoteMonitorURL = helperURL;
		helperURL = prefService.get(LoadEarlierProgress.class,"projectName");
		if (helperURL != null) projectName = helperURL;

		discoverInputFiles();
	}

	// ----------------- network options -----------------
	@Parameter(label = "Read also from a remote monitor:",
		description = "Request that the progress files shall be retrieved also from a remote host.",
		callback = "rebuildDialog")
	private boolean readAlsoFromRemoteMonitor = false;

	@Parameter(label = "URL address of the remote monitor:",
		description = "This entry is ignored if the above is not checked.",
		persistKey = "remoteMonitorURL",
		callback = "remoteAuxDataFlagSetter")
	private String remoteMonitorURL = "setHereServerAddress:"+ DatasetServer.defaultPort;

	@Parameter(label = "Project name on the remote monitor:",
		description = "This entry is ignored if the above is not checked.",
		persistKey = "projectName",
		callback = "remoteAuxDataFlagSetter")
	private String projectName = "setHereProjectName";

	// notifies if user has made any change in the two above fields
	private boolean remoteAuxDataChangedFlag = false;
	private void remoteAuxDataFlagSetter() { remoteAuxDataChangedFlag = true; }

	private
	void rebuildDialog()
	{
		// if user has updated aux network data and readAlsoFromRemoteMonitor was true,
		// we assume he/she noticed a mistake in the aux network data and we will therefore
		// open new dialog again with the readAlsoFromRemoteMonitor set to true
		if (remoteAuxDataChangedFlag && !readAlsoFromRemoteMonitor) readAlsoFromRemoteMonitor = true;
		remoteAuxDataChangedFlag = false;

		this.cancel("");
		this.saveInputs();
		//LoadEarlierProgress reads 'remoteMonitorURL' itsway... so we have to save thatway too
		prefService.put(LoadEarlierProgress.class,"remoteMonitorURL",remoteMonitorURL);
		prefService.put(LoadEarlierProgress.class,"projectName",projectName);
		logService.warn("Deprecating this dialog and opening new with fresh list of detected files.");
		this.getContext().getService(CommandService.class).run(
				LoadEarlierProgress.class, true,
				"logService",  logService, "prefService", prefService,
				"appModel", appModel );
	}


	// ----------------- available file names -----------------
	@Parameter(label = "Choose from the detected files:", persist = false, choices = {})
	private String lineageFilenameStr = "none available yet";

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
			prefService.put(LoadEarlierProgress.class,"projectName",projectName);

			//second, start populating the list of discovered input files
			final List<String> localOnlyFiles  = new LinkedList<>();
			final List<String> syncedFiles     = new LinkedList<>();
			final List<String> remoteOnlyFiles = new LinkedList<>();

			//local files
			logService.info("Reading from "+projectRootFoldername);
			LineageFiles.listLineageFiles(projectRootFoldername)
			  .forEach( p -> localOnlyFiles.add(p.getFileName().toString()) );

			//remote files
			if (readAlsoFromRemoteMonitor)
			{
				remoteMonitorURL = FileTransfer.fixupURL(remoteMonitorURL);
				final String URL = remoteMonitorURL + "/" + projectName;
				logService.info("Reading from "+URL);
				FileTransfer.listAvailableFiles(URL)
				  .forEach( p -> enlistNewInputFile(localOnlyFiles,syncedFiles,remoteOnlyFiles,p) );
			}

			//merge (and prefix) the known input files into a single list
			final List<String> choices = new ArrayList<>(localOnlyFiles.size()+syncedFiles.size()+remoteOnlyFiles.size());
			localOnlyFiles.forEach(  f -> choices.add("Local only : "+f));
			syncedFiles.forEach(     f -> choices.add("Synced     : "+f));
			remoteOnlyFiles.forEach( f -> choices.add("Remote only: "+f));
			//choices.forEach(s -> System.out.println(">>"+s+"<<"));

			getInfo().getMutableInput("lineageFilenameStr", String.class).setChoices( choices );
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
		if (this.isCanceled())
		{
			logService.info("This dialog was deprecated, doing nothing.");
			return;
		}

		final boolean doRemoteRead = lineageFilenameStr.startsWith("Remote");

		//fixup the filename and test it for validity
		lineageFilenameStr = lineageFilenameStr.substring(13);
		if (!LineageFiles.lineageFilePattern.test(lineageFilenameStr)) return;

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
				final String URL = remoteMonitorURL + "/" + projectName;
				logService.info("Loading from remote URL: " + URL);
				FileTransfer.getParticularFile(URL, lineageFilenameStr, projectRootFoldername);
				//files arrives to the local folder....
			}

			final Path lineageFullFilename = projectRootFoldername.resolve(lineageFilenameStr);
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
			MergeModelDialog mergeParams = null;
			try {
				mergeParams = (MergeModelDialog)context()
						.getService(CommandService.class)
						.run(MergeModelDialog.class,true)
						.get().getCommand();
			} catch (InterruptedException | ExecutionException e) {
				logService.error("Couldn't create or read merging parameters:");
				e.printStackTrace();
				return;
			}
			if (mergeParams == null || !mergeParams.wasExecuted)
			{
				logService.info("User canceled dialog");
				return;
			}

			//create an extra copy (duplicate) of the current lineage,
			//the current lineage 'refModel' will be filled with the merged data
			//
			//duplicate by exporting & importing -- which we sell as backup before the merge...
			final Model copyModel = LineageFiles.createEmptyModelWithUnitsOf(refModel);
			try {
				final Path auxBackupFile = projectRootFoldername.resolve("backupCurrentLineageBeforeMerge.mstdn");
				LineageFiles.saveModelIntoLineageFile(refModel, auxBackupFile);
				LineageFiles.loadLineageFileIntoModel(auxBackupFile, copyModel);
			} catch (IOException e) {
				logService.error("Failed backing up the lineage file!");
				e.printStackTrace();
				return;
			}

			final MergeDatasets.OutputDataSet output = new MergeDatasets.OutputDataSet( refModel );
			MergeModels.merge( copyModel, newModel, output,
				mergeParams.distCutoff, mergeParams.mahalanobisDistCutoff, mergeParams.ratioThreshold );

			logService.info("Merged the current lineage (A) with "+copyModel.getGraph().vertices().size()
					+ " vertices and " + copyModel.getGraph().edges().size() +" edges together");
			logService.info("  with the loaded lineage (B) with "+newModel.getGraph().vertices().size()
					+ " vertices and " + newModel.getGraph().edges().size() +" edges");
			logService.info("  to create a new lineage with "+refModel.getGraph().vertices().size()
					+ " vertices and " + refModel.getGraph().edges().size() +" edges.");
		}
		//
		refModel.getGraph().notifyGraphChanged();
		LineageFiles.finishImportingModel(newModel);
	}
}
