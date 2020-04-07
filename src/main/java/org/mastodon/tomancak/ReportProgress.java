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
import java.io.File;
import java.io.IOException;

@Plugin( type = Command.class, name = "Mastodon ReportProgress plugin" )
public class ReportProgress
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MastodonPluginAppModel appModel;

	// ----------------- user name and file name -----------------
	@Parameter(label = "Identify yourself as:",
		description = "Please, choose your nickname using only common characters -- this will be part of an output filename.",
		initializer = "initLineageFile", callback = "updateLineageFile")
	private String userName = System.getProperty("user.name");

	@Parameter(label = "Going to write file:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false)
	private String lineageFilename;

	//cannot be initialized when the object is created because the 'appModel' would not be yet available
	private String projectRootFoldername;

	private
	void initLineageFile()
	{
		projectRootFoldername = LineageFiles.getProjectRootFoldername(appModel);

		//try to retrieve the 'lineageFilename' from the preferences
		final PrefService ps = logService.getContext().getService(PrefService.class);
		if (ps != null)
			userName = ps.get(ReportProgress.class,"userName");

		//finally, set up the 'lineageFilename'
		updateLineageFile();
	}

	private
	void updateLineageFile()
	{
		lineageFilename = LineageFiles.lineageFilename(projectRootFoldername,userName);
	}

	// ----------------- network options -----------------
	@Parameter(label = "Report also to a remote monitor:",
		description = "Select if the current progress shall be saved also on a remote host.")
	private boolean sendAlsoToRemoteMonitor = false;

	@Parameter(label = "URL address of the remote monitor:",
		description = "This entry is ignored if the above is note checked.")
	private String remoteMonitorURL = "";


	// ----------------- implementation -----------------
	@Override
	public void run()
	{
		//bail out if we are started incorrectly...
		if (appModel == null) return;

		//test if the file can be created at all -- main worry is about
		//the content of the 'userName' part
		try {
			new File(lineageFilename).createNewFile();
		} catch (IOException e) {
			logService.error("Cannot create lineage file: "+lineageFilename);
			e.printStackTrace();
			return;
		}

		//ok, create-able, let's export data then
		final Model model = appModel.getAppModel().getModel();
		try {
			LineageFiles.saveModelIntoLineageFile(model,lineageFilename);

			if (sendAlsoToRemoteMonitor)
			{
				logService.info("saving also to remote URL: "+remoteMonitorURL);
			}
		} catch (IOException e) {
			logService.error("Failed saving the lineage file!");
			e.printStackTrace();
		}

		logService.info("Saved lineage with "+model.getGraph().vertices().size()
			+ " vertices and " + model.getGraph().edges().size() +" edges");
	}
}
