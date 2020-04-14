package org.mastodon.tomancak;

import java.io.File;
import java.util.*;
import javax.swing.UIManager;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.Mastodon;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;
import org.mastodon.tomancak.util.LineageFiles;

import net.imagej.ImageJ;
import org.scijava.AbstractContextual;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.prefs.PrefService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin( type = MastodonPlugin.class )
public class CollabMastodonPlugins extends AbstractContextual implements MastodonPlugin
{
	private static final String SAVE_MODEL_SNAPSHOT = "[tomancak] save current lineage";
	private static final String LOAD_MODEL_SNAPSHOT = "[tomancak] load external lineage";

	private static final String[] SAVE_MODEL_SNAPSHOT_KEYS = { "not mapped" };
	private static final String[] LOAD_MODEL_SNAPSHOT_KEYS = { "not mapped" };

	private static Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( SAVE_MODEL_SNAPSHOT, "Save Current Lineage" );
		menuTexts.put( LOAD_MODEL_SNAPSHOT, "Load External Lineage" );
	}

	/** Command descriptions for all provided commands */
	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigContexts.TRACKSCHEME, KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( SAVE_MODEL_SNAPSHOT, SAVE_MODEL_SNAPSHOT_KEYS, "Export the current complete lineage into file in the folder next to your .mastodon project file." );
			descriptions.add( LOAD_MODEL_SNAPSHOT, LOAD_MODEL_SNAPSHOT_KEYS, "Replaces the current complete lineage with that from a chosen file." );
		}
	}


	private final AbstractNamedAction saveModelSnapshotAction;
	private final AbstractNamedAction loadModelSnapshotAction;

	public CollabMastodonPlugins()
	{
		saveModelSnapshotAction = new RunnableAction( SAVE_MODEL_SNAPSHOT, this::saveModelSnapshot );
		loadModelSnapshotAction = new RunnableAction( LOAD_MODEL_SNAPSHOT, this::loadModelSnapshot );
		updateEnabledActions();
	}

	private MastodonPluginAppModel pluginAppModel;

	@Override
	public void setAppModel( final MastodonPluginAppModel model )
	{
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Arrays.asList(
				menu( "Plugins",
						menu( "Racing",
								item( SAVE_MODEL_SNAPSHOT ),
								item( LOAD_MODEL_SNAPSHOT ) ) ) );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public void installGlobalActions( final Actions actions )
	{
		actions.namedAction( saveModelSnapshotAction, SAVE_MODEL_SNAPSHOT_KEYS );
		actions.namedAction( loadModelSnapshotAction, LOAD_MODEL_SNAPSHOT_KEYS );
	}

	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		saveModelSnapshotAction.setEnabled( appModel != null );
		loadModelSnapshotAction.setEnabled( appModel != null );
	}


	private void saveModelSnapshot()
	{
		this.getContext().getService(CommandService.class).run(
			ReportProgress.class, true,
			"logService",  this.getContext().getService(LogService.class),
			"prefService", this.getContext().getService(PrefService.class),
			"appModel", pluginAppModel
		);
	}


	private void loadModelSnapshot()
	{
		this.getContext().getService(CommandService.class).run(
			LoadEarlierProgress.class, true,
			"logService",  this.getContext().getService(LogService.class),
			"prefService", this.getContext().getService(PrefService.class),
			"appModel", pluginAppModel
		);
	}


	/*
	 * Start Mastodon ...
	 */
	public static void main( final String[] args ) throws Exception
	{
		Locale.setDefault( Locale.US );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		//start up our own Fiji/Imagej2
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Mastodon mastodon = (Mastodon)ij.command().run(Mastodon.class, true).get().getCommand();
		mastodon.setExitOnClose();
		mastodon.openProject( new MamutProject(
			new File( "/Users/ulman/data/p_Johannes/Polyclad/2019-09-06_EcNr2_NLSH2B-GFP_T-OpenSPIM_singleTP.mastodon" ),
			new File( "/Users/ulman/data/p_Johannes/Polyclad/2019-09-06_EcNr2_NLSH2B-GFP_T-OpenSPIM_singleTP.xml" ) ) );
	}
}
