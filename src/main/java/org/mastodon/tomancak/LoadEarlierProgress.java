package org.mastodon.tomancak;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.tomancak.util.LineageFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

@Plugin( type = Command.class, name = "Mastodon LoadEarlierProgress plugin" )
public class LoadEarlierProgress
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MastodonPluginAppModel appModel;

	// ----------------- available file names -----------------
	@Parameter(label = "Searching in this folder:",
		visibility = ItemVisibility.MESSAGE, required = false, persist = false,
		initializer = "discoverInputFiles")
	private String projectRootFoldername;
	//NB: cannot be initialized when the object is created because the 'appModel' would not be yet available

	@Parameter(label = "Choose from the detected files:", persist = false, choices = {})
	private String lineageFilename = "none available yet";

	private
	void discoverInputFiles()
	{
		projectRootFoldername = LineageFiles.getProjectRootFoldername(appModel);

		//read choices
		try {
			final ArrayList<String> choices = new ArrayList<>(20);
			LineageFiles.listLineageFiles(projectRootFoldername).forEach(p -> {
				choices.add(p.getFileName().toString());
				//String f = p.getFileName().toString();
				//System.out.println( ">>" + LineageFiles.authorOfLineageFile(f)+"<< @ >>"+LineageFiles.dateOfLineageFile(f) +"<<");
			});
			getInfo().getMutableInput("lineageFilename", String.class).setChoices( choices );
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
			LineageFiles.loadLineageFileIntoModel(projectRootFoldername + File.separator + lineageFilename,model);
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
