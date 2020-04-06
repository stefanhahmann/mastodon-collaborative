package org.mastodon.tomancak;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.UIManager;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.Mastodon;
import org.mastodon.revised.model.AbstractModelImporter;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;
import org.scijava.AbstractContextual;
import org.scijava.Context;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin( type = MastodonPlugin.class )
public class TomancakPlugins extends AbstractContextual implements MastodonPlugin
{
	private static final String SAVE_MODEL_SNAPSHOT = "[tomancak] save current lineage";
	private static final String LOAD_MODEL_SNAPSHOT = "[tomancak] load external lineage";

	private static final String[] SAVE_MODEL_SNAPSHOT_KEYS = { "ctrl S" };
	private static final String[] LOAD_MODEL_SNAPSHOT_KEYS = { "ctrl L" };

	private static Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( SAVE_MODEL_SNAPSHOT, "Save Current Lineage" );
		menuTexts.put( LOAD_MODEL_SNAPSHOT, "Load External Lineage" );
	}

	/*
	 * Command descriptions for all provided commands
	 */
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

	public TomancakPlugins()
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

	String projectRootFolder()
	{
		final File pRoot = pluginAppModel.getWindowManager().getProjectManager().getProject().getProjectRoot();
		final String pFolder = pRoot.isDirectory()? pRoot.getAbsolutePath() : pRoot.getParentFile().getAbsolutePath();

		//System.out.println("(debug) pRoot : "+pRoot.getAbsolutePath());
		//System.out.println("(debug) parent: "+pRoot.getParentFile().getAbsolutePath());

		return pFolder;
	}

	private void saveModelSnapshot()
	{
		if ( pluginAppModel == null ) return;

		System.out.println("saveModelSnapshot()");

		//user input
		final String userName = "VladoDaTracker";

		//setup the lineage filename
		final String filename = lineageFile(projectRootFolder(),userName);

		System.out.println("(final) file  : "+filename);

		final Model model = pluginAppModel.getAppModel().getModel();
		try {
			//test if such file can be created at all (main worry is about the content of the 'userName' part
			new File(filename).createNewFile();
		} catch (IOException e) {
			System.out.println("cannot create the given file!");
			return;
		}

		try {
			//hmm, feels like we can write it, just do it...
			final ZippedModelWriter writer = new ZippedModelWriter(filename);
			model.saveRaw( writer );
			writer.close();
		} catch (IOException e) {
			System.out.println("Some error writing the model: ");
			e.printStackTrace();
		}

		System.out.println("Saved model with "+ model.getSpaceUnits() + " and " + model.getTimeUnits());
		System.out.println("Model has "+model.getGraph().vertices().size() + " vertices and " + model.getGraph().edges().size() +" edges");
	}


	private void loadModelSnapshot()
	{
		if ( pluginAppModel == null ) return;

		System.out.println("loadModelSnapshot()");

		//create new Model of the same params:
		final Model refModel = pluginAppModel.getAppModel().getModel();
		final Model newModel = new Model(refModel.getSpaceUnits(),refModel.getTimeUnits());

		System.out.println("Empty model with "+ newModel.getSpaceUnits() + " and " + newModel.getTimeUnits());
		System.out.println("Model has "+newModel.getGraph().vertices().size() + " vertices and " + newModel.getGraph().edges().size() +" edges");

		try {
			//load into an extra GraphModel
			//loadLineageFileIntoModel("/temp/test.dat",newModel);

			//load into the current GraphModel
			new AbstractModelImporter< Model >( refModel ){{ startImport(); }};
			loadLineageFileIntoModel("/temp/test.dat",refModel);
			new AbstractModelImporter< Model >( refModel ){{ finishImport(); }};
		} catch (IOException e) {
			System.out.println("Some error reading the model: ");
			e.printStackTrace();
		}

		System.out.println("Loaded model with "+ newModel.getSpaceUnits() + " and " + newModel.getTimeUnits());
		System.out.println("Model has "+newModel.getGraph().vertices().size() + " vertices and " + newModel.getGraph().edges().size() +" edges");
	}


	void loadLineageFileIntoModel(final String filename, final Model model) throws IOException
	{
		final ZippedModelReader reader = new ZippedModelReader(filename);
		model.loadRaw( reader );
		reader.close();
	}


	static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd__HH:mm:ss__");
	final static Predicate<String> lineageFilePattern =
		Pattern.compile("[2-9][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9]__[012][0-9]:[0-5][0-9]:[0-5][0-9]__.*\\.mstdn").asPredicate();

	static String lineageFile(final String parentFolder, final String userName)
	{
		return (parentFolder + File.separator + dateFormatter.format(new Date()) + userName + ".mstdn");
	}

	static Stream<Path> listLineageFiles(final String parentFolder) throws IOException
	{
		return Files
		         .walk(Paths.get(parentFolder),1)
		         .filter( p -> lineageFilePattern.test(p.getFileName().toString()) );
	}

	static String dateOfLineageFile(final String filename)
	{
		return filename.substring(0,20);
	}

	static String authorOfLineageFile(final String filename)
	{
		return filename.substring(22, filename.length()-6);
	}


	/*
	 * Start Mastodon ...
	 */
	public static void main( final String[] args ) throws Exception
	{
		Locale.setDefault( Locale.US );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final Mastodon mastodon = new Mastodon();
		new Context().inject( mastodon );
		mastodon.run();
		mastodon.setExitOnClose();
		mastodon.openProject( new MamutProject(
			new File( "/Users/ulman/data/p_Johannes/Polyclad/2019-09-06_EcNr2_NLSH2B-GFP_T-OpenSPIM_singleTP.mastodon" ),
			new File( "/Users/ulman/data/p_Johannes/Polyclad/2019-09-06_EcNr2_NLSH2B-GFP_T-OpenSPIM_singleTP.xml" ) ) );
	}

	public static void demoListingFiles( final String[] args ) throws Exception
	{
		listLineageFiles("/temp").forEach(p -> {
			String f = p.getFileName().toString();
			System.out.println( ">>" + authorOfLineageFile(f)+"<< @ >>"+dateOfLineageFile(f) +"<<");
		});
	}
}
