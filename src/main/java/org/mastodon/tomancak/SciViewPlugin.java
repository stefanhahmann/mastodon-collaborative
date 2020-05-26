package org.mastodon.tomancak;

import bdv.util.Bounds;
import bdv.viewer.ConverterSetups;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import graphics.scenery.Node;
import graphics.scenery.Sphere;
import graphics.scenery.volumes.Colormap;
import graphics.scenery.volumes.TransferFunction;
import graphics.scenery.volumes.Volume;

import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;
import org.joml.*;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.MamutViewBdv;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;

import org.mastodon.spatial.SpatialIndex;
import org.scijava.AbstractContextual;
import org.scijava.command.CommandService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

import org.scijava.event.EventService;
import sc.iview.event.NodeAddedEvent;

import bdv.viewer.SourceAndConverter;
import sc.iview.SciView;
import sc.iview.commands.view.SetTransferFunction;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin( type = MastodonPlugin.class )
public class SciViewPlugin extends AbstractContextual implements MastodonPlugin
{
	private static final String SCIVIEW_CONNECTOR = "[tomancak] start SciView";
	private static final String[] SCIVIEW_CONNECTOR_KEYS = { "not mapped" };

	private static Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( SCIVIEW_CONNECTOR, "Start SciView" );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Arrays.asList(
				menu( "Plugins",
						item( SCIVIEW_CONNECTOR ) ) );
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
			descriptions.add( SCIVIEW_CONNECTOR, SCIVIEW_CONNECTOR_KEYS, "Start SciView and a special Mastodon->SciView connector control panel." );
		}
	}


	private final AbstractNamedAction startSciViewConnectorAction;

	public SciViewPlugin()
	{
		startSciViewConnectorAction = new RunnableAction( SCIVIEW_CONNECTOR, this::startSciViewConnector );
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
	public void installGlobalActions( final Actions actions )
	{
		actions.namedAction( startSciViewConnectorAction, SCIVIEW_CONNECTOR_KEYS );
	}

	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		startSciViewConnectorAction.setEnabled( appModel != null );
	}


	private void startSciViewConnector()
	{
		new Thread("Mastodon's SciView")
		{
			@Override
			public void run()
			{
				//find out if we are started from some BDV window
				class FocusedBdvWindow {
					MamutViewBdv focusedBdvWindow = null;
					void setPossiblyTo(MamutViewBdv adept) { if (adept.getFrame().isFocused()) focusedBdvWindow = adept; }
					boolean isThereSome() { return focusedBdvWindow != null; }
					MamutViewBdv get() { return focusedBdvWindow; }
				}
				final FocusedBdvWindow focusedBdvWindow = new FocusedBdvWindow();
				pluginAppModel.getWindowManager().forEachBdvView( bdvView -> focusedBdvWindow.setPossiblyTo(bdvView) );

				//start the SciView
				SciView sv;
				try {
					sv = SciView.create();
					sv.setInterpreterWindowVisibility(false);
					Thread.sleep(2000); //a bit of a grace time before we continue
					System.out.println("SciView started...");
				} catch (Exception e) {
					System.out.println("SciView has a problem:");
					e.printStackTrace();
					return;
				}

				final String volumeName = "Mastodon's raw data";
				final SourceAndConverter<?> sac = pluginAppModel.getAppModel().getSharedBdvData().getSources().get(0);
				//
				final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac, volumeName);

				//adjust the transfer function to a "diagonal"
				TransferFunction tf = v.getTransferFunction();
				tf.clear();
				tf.addControlPoint(0.00f, 0.0f);
				tf.addControlPoint(0.05f, 0.1f);
				tf.addControlPoint(0.90f, 0.7f);
				tf.addControlPoint(1.00f, 0.8f);

				setVolumeColorFromMastodon(v);
				//may setup own display range if need-be

				v.setName(volumeName);
				v.removeChild( v.getChildren().get(0) ); //removes the grid node
				v.setWantsComposeModel(false); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
				final float scale = 0.5f;      //alternative to v.setPixelToWorldRatio(scale);
				v.setModel( new Matrix4f(scale,0,0,0, 0,-scale,0,0, 0,0,-scale,0, 0,0,0,1) );
				v.setNeedsUpdateWorld(true);
				//now the volume's diagonal in world coords is now:
				//      [0,0,0] -> [scale*origXSize, -scale*origYSize, -scale*origZSize]

				//define various listeners:
				//
				//watch when BDV (through its color&brightness dialog) changes display range or volume's color
				pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().listeners().add( t -> {
					System.out.println("BDV says display range: " + t.getDisplayRangeMin() + " -> " + t.getDisplayRangeMax());
					System.out.println("BDV says new color    : " + t.getColor());

					//request that the volume be repainted in SciView
					setVolumeColorFromMastodon(v);
					v.getVolumeManager().requestRepaint();
				});

				//watch when SciView's control panel adjusts the color
				//and re-reset it back to that of Mastodon
				v.getConverterSetups().get(0).setupChangeListeners().add( (t) -> {
					//read out the current min-max setting (which has been just re-set via the SciView's nodes panel)
					final double min = t.getDisplayRangeMin();
					final double max = t.getDisplayRangeMax();

					System.out.println("SciView says display range: " + min +" -> "+ max  );
					System.out.println("SciView says new color    : " + t.getColor() );
					//
					//be of the current Mastodon's color -- essentially,
					//ignores (by re-setting back) whatever LUT choice has been made in SciView's nodel panel
					setVolumeColorFromMastodon(v);

					final ConverterSetups setups = pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups();
					setups.getBounds().setBounds( setups.getConverterSetup(sac), new Bounds(min,max) );
					pluginAppModel.getWindowManager().forEachBdvView( view -> view.requestRepaint() );
				});

				//this block may set up a listener for TP change ot a BDV from which SciView was started, if any...
				if (focusedBdvWindow.isThereSome())
				{
					System.out.println("Will be syncing timepoints with "+focusedBdvWindow.get().getFrame().getTitle());
				}
				else System.out.println("Will NOT be syncing timepoints to any BDV window");

				final MamutViewBdv bdv = pluginAppModel.getWindowManager().createBigDataViewer();
				bdv.getViewerPanelMamut().addRenderTransformListener(a -> System.out.println("Here's BDV new view: "+printArray(a.getRowPackedCopy())));
				bdv.getViewerPanelMamut().addTimePointListener(a -> System.out.println("Here's BDV new TP: "+a));
				System.out.println("BDV window name: "+bdv.getFrame().getTitle());

				//start the TransferFunction modifying dialog
				getContext().getService(CommandService.class).run(SetTransferFunction.class,true,
						"sciView",sv,"volume",v);

				//--------------------------

				EventService events = sv.getScijavaContext().getService(EventService.class);
				System.out.println("Found an event service: "+events);

				SpatialIndex<Spot> spots = pluginAppModel.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(0);

				final Node spotsNode = new Node("spots"); //fake "grouping" node
				//sv.addChild(spotsNode); //adds node to the scene
				sv.addNode(spotsNode);    //adds node to the scene and displays it in the Inspector

				float[] pos = new float[3];
				for (Spot spot : spots)
				{
					spot.localize(pos);
					pos[0] *= +scale; //adjust coords to the current volume scale
					pos[1] *= -scale;
					pos[2] *= -scale;

					final Sphere sph = new Sphere(5.0f, 8); //radius could be scaled too...
					sph.setName(spot.getLabel());
					sph.setPosition(pos);
					spotsNode.addChild(sph);
					events.publish(new NodeAddedEvent(sph));
				}
			}
		}.start();
	}

	public static
	String printArray(long... array)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (Object a : array) sb.append(a+",");
		sb.append("]\n");
		return sb.toString();
	}
	public static
	String printArray(float... array)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (Object a : array) sb.append(a+",");
		sb.append("]\n");
		return sb.toString();
	}
	public static
	String printArray(double... array)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (Object a : array) sb.append(a+",");
		sb.append("]\n");
		return sb.toString();
	}

	void setVolumeColorFromMastodon(final Volume v)
	{
		int rgba = v.getConverterSetups().get(0).getColor().get();
		int r = ARGBType.red( rgba );
		int g = ARGBType.green( rgba );
		int b = ARGBType.blue( rgba );
		//int a = ARGBType.alpha( rgba );
		//System.out.println("setVolumeCOlor to "+r+","+g+","+b+","+a);
		v.setColormap(Colormap.fromColorTable(new ColorTable8( createMapArray(r,g,b) )));
	}

	byte[][] createMapArray(int r, int g, int b)
	{
		final byte[][] map = new byte[3][256];
		for (int i = 0; i < 256; ++i)
		{
			float ratio = (float)i / 256f;
			map[0][i] = (byte)(ratio * (float)r);
			map[1][i] = (byte)(ratio * (float)g);
			map[2][i] = (byte)(ratio * (float)b);
		}
		return map;
	}
}
