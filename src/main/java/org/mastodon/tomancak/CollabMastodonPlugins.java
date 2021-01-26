package org.mastodon.tomancak;

import java.util.*;
import javax.swing.UIManager;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.Mastodon;

import net.imagej.ImageJ;
import org.mastodon.ui.keymap.CommandDescriptionProvider;
import org.mastodon.ui.keymap.CommandDescriptions;
import org.mastodon.ui.keymap.KeyConfigContexts;
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

@Plugin( type = MamutPlugin.class )
public class CollabMastodonPlugins extends AbstractContextual implements MamutPlugin
{
	private static final String SAVE_MODEL_SNAPSHOT = "[collab] save current lineage";
	private static final String LOAD_MODEL_SNAPSHOT = "[collab] load external lineage";
	private static final String CREATE_SNAPSHOT_SPACE = "[collab] create project space";
	private static final String DELETE_SNAPSHOT_SPACE = "[collab] delete project space";

	private static final String[] SAVE_MODEL_SNAPSHOT_KEYS = { "not mapped" };
	private static final String[] LOAD_MODEL_SNAPSHOT_KEYS = { "not mapped" };
	private static final String[] CREATE_SNAPSHOT_SPACE_KEYS = { "not mapped" };
	private static final String[] DELETE_SNAPSHOT_SPACE_KEYS = { "not mapped" };

	private static final Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( SAVE_MODEL_SNAPSHOT, "Save Current Lineage" );
		menuTexts.put( LOAD_MODEL_SNAPSHOT, "Load External Lineage" );
		menuTexts.put( CREATE_SNAPSHOT_SPACE, "Create Project Space" );
		menuTexts.put( DELETE_SNAPSHOT_SPACE, "Delete Project Space" );
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
			descriptions.add( SAVE_MODEL_SNAPSHOT, SAVE_MODEL_SNAPSHOT_KEYS, "Export the current complete lineage into file in the folder next to your .mastodon project file, possibly sends it away to a remote server." );
			descriptions.add( LOAD_MODEL_SNAPSHOT, LOAD_MODEL_SNAPSHOT_KEYS, "Replaces the current complete lineage with that from a chosen file, or possibly from a remote server." );
			descriptions.add( CREATE_SNAPSHOT_SPACE, CREATE_SNAPSHOT_SPACE_KEYS, "Opens a new project space on a remote server for storing lineage files." );
			descriptions.add( DELETE_SNAPSHOT_SPACE, DELETE_SNAPSHOT_SPACE_KEYS, "Closes and deletes project space on a remote server." );
		}
	}


	private final AbstractNamedAction saveModelSnapshotAction;
	private final AbstractNamedAction loadModelSnapshotAction;
	private final AbstractNamedAction createSnapshotSpaceAction;
	private final AbstractNamedAction deleteSnapshotSpaceAction;

	public CollabMastodonPlugins()
	{
		saveModelSnapshotAction = new RunnableAction( SAVE_MODEL_SNAPSHOT, this::saveModelSnapshot );
		loadModelSnapshotAction = new RunnableAction( LOAD_MODEL_SNAPSHOT, this::loadModelSnapshot );
		createSnapshotSpaceAction = new RunnableAction( CREATE_SNAPSHOT_SPACE, this::createSnapshotSpace );
		deleteSnapshotSpaceAction = new RunnableAction( DELETE_SNAPSHOT_SPACE, this::deleteSnapshotSpace );
		updateEnabledActions();
	}

	private MamutPluginAppModel pluginAppModel;

	@Override
	public void setAppPluginModel( final MamutPluginAppModel model )
	{
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Collections.singletonList(
				menu("Plugins",
						menu("Collaboration",
								item(SAVE_MODEL_SNAPSHOT),
								item(LOAD_MODEL_SNAPSHOT),
								item(CREATE_SNAPSHOT_SPACE),
								item(DELETE_SNAPSHOT_SPACE))));
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
		actions.namedAction( createSnapshotSpaceAction, CREATE_SNAPSHOT_SPACE_KEYS );
		actions.namedAction( deleteSnapshotSpaceAction, DELETE_SNAPSHOT_SPACE_KEYS );
	}

	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		saveModelSnapshotAction.setEnabled( appModel != null );
		loadModelSnapshotAction.setEnabled( appModel != null );
		createSnapshotSpaceAction.setEnabled( appModel != null );
		deleteSnapshotSpaceAction.setEnabled( appModel != null );
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


	private void createSnapshotSpace()
	{
		this.getContext().getService(CommandService.class).run(
			CreateProject.class, true,
			"logService",  this.getContext().getService(LogService.class),
			"prefService", this.getContext().getService(PrefService.class)
		);
	}


	private void deleteSnapshotSpace()
	{
		this.getContext().getService(CommandService.class).run(
			DeleteProject.class, true,
			"logService",  this.getContext().getService(LogService.class),
			"prefService", this.getContext().getService(PrefService.class)
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
	}
}
