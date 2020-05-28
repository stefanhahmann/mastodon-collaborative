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
import org.mastodon.model.FocusModel;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.MamutViewBdv;
import org.mastodon.revised.mamut.MamutViewTrackScheme;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.coloring.GraphColorGenerator;
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
import sc.iview.event.NodeChangedEvent;

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
				final DisplayMastodonData dmd = new DisplayMastodonData(pluginAppModel);
				dmd.controllingBdvWindow.setupFrom(pluginAppModel);

				try {
					dmd.sv = SciView.create();
					dmd.sv.setInterpreterWindowVisibility(false);
					Thread.sleep(2000); //a bit of a grace time before we continue
					System.out.println("SciView started...");
				} catch (Exception e) {
					System.out.println("SciView has a problem:");
					e.printStackTrace();
					dmd.sv = null;
				}
				if (dmd.sv == null) return;

				//find the event service to be able to notify the inspector
				dmd.events = dmd.sv.getScijavaContext().getService(EventService.class);
				System.out.println("Found an event service: " + dmd.events);

				//show one volume
				//Volume v = dmd.showOneTimePoint(10);

				//show full volume
				Volume v = dmd.showTimeSeries();
				dmd.makeSciViewReadBdvSetting(v);

				//show spots
				final Node spotsNode = new Node("Mastodon spots");
				dmd.centerNodeOnVolume(spotsNode,v); //so that shift+mouse rotates nicely
				dmd.sv.addNode(spotsNode);

				if (dmd.controllingBdvWindow.isThereSome())
				{
					//setup coloring
					final MamutViewTrackScheme tsWin = pluginAppModel.getWindowManager().createTrackScheme();
					tsWin.getColoringModel().listeners().add( () -> {
						System.out.println("coloring changed");
						setColorGeneratorFrom(tsWin);
					});

					//setup focusing
					sRef = pluginAppModel.getAppModel().getModel().getGraph().vertexRef();
					final FocusModel<Spot, Link> focus = pluginAppModel.getAppModel().getFocusModel();
					focus.listeners().add( () -> {
						focus.getFocusedVertex(sRef);
						System.out.println("focused on "+sRef.getLabel());
						updateFocus( dmd.sv.find(sRef.getLabel()) );
					});

					dmd.controllingBdvWindow.get()
							.getViewerPanelMamut()
							.addTimePointListener( tp -> {
								updateFocus(null);
								dmd.showSpots(tp,spotsNode,colorGenerator);
							} );
				}

				dmd.sv.getFloor().setVisible(false);
				dmd.sv.centerOnNode(spotsNode);
			}
		}.start();
	}

	//coloring attributes
	private GraphColorGenerator<Spot, Link> colorGenerator = null;
	private void setColorGeneratorFrom(final MamutViewTrackScheme tsWin)
	{
		colorGenerator = tsWin.getGraphColorGeneratorAdapter().getColorGenerator();
	}

	//focus attributes
	private Node stillFocusedNode = null;
	private Spot sRef;
	private void updateFocus(final Node newFocusNode)
	{
		/* DEBUG
		System.out.println("defocus: "+(stillFocusedNode != null ? stillFocusedNode.getName() : "NONE")
			+", focus newly: "+(newFocusNode != null ? newFocusNode.getName() : "NONE"));
		*/

		//something to defocus?
		if (stillFocusedNode != null && stillFocusedNode.getParent() != null)
		{
			stillFocusedNode.setScale(normalScale);
			stillFocusedNode.setNeedsUpdate(true);
		}

		//something to focus?
		stillFocusedNode = newFocusNode;
		if (stillFocusedNode != null)
		{
			stillFocusedNode.setScale(focusedScale);
			stillFocusedNode.setNeedsUpdate(true);
		}
	}
	static private Vector3f normalScale = new Vector3f(1.0f);
	static private Vector3f focusedScale = new Vector3f(1.5f);
}
