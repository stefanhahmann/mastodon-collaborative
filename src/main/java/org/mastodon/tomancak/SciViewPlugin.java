package org.mastodon.tomancak;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.Bounds;
import bdv.viewer.Source;
import graphics.scenery.Sphere;
import graphics.scenery.volumes.Colormap;
import graphics.scenery.volumes.TransferFunction;
import graphics.scenery.volumes.Volume;

import net.imglib2.display.ColorTable8;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.joml.*;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
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

import bdv.viewer.SourceAndConverter;
import sc.iview.SciView;
import sc.iview.commands.view.SetTransferFunction;

import java.lang.Math;
import java.text.NumberFormat;
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
				SciView sv;
				try {
					sv = SciView.create();
					Thread.sleep(2000); //a bit of a grace time before we continue
					System.out.println("SciView started...");
				} catch (Exception e) {
					System.out.println("SciView has a problem:");
					e.printStackTrace();
					return;
				}

				final String volumeName = "Mastodon's raw data";
				final SourceAndConverter<?> sac = pluginAppModel.getAppModel().getSharedBdvData().getSources().get(0);

				final Source<?> s = sac.getSpimSource();
				final float[] voxelDims = new float[s.getVoxelDimensions().numDimensions()];
				for (int d = 0; d < voxelDims.length; ++d)
				{
					voxelDims[d] = (float)s.getVoxelDimensions().dimension(d);
					//System.out.print(voxelDims[d]+"  ");
				}
				//System.out.println("are the voxel dimensions");

				//image size -- Ulrik suggested that spot coords should be normalized w.r.t. image size,
				//but it turned out this is not needed.. so this piece of code has no use
				final long imgSize[] = new long[3];
				s.getSource(0,0).dimensions(imgSize);
				System.out.println("ImgSize: "+printArray(imgSize));

				//crank up the volume :-)
				final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac, volumeName);

				setVolumeColorFromMastodon(v);
				//may setup own display range if need-be

				//adjust the transfer function to a "diagonal"
				TransferFunction tf = v.getTransferFunction();
				tf.clear();
				tf.addControlPoint(0.00f, 0.0f);
				tf.addControlPoint(0.05f, 0.1f);
				tf.addControlPoint(0.90f, 0.7f);
				tf.addControlPoint(1.00f, 0.8f);

				pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().listeners().add( t -> {
					System.out.println("BDV says display range: " + t.getDisplayRangeMin() + " -> " + t.getDisplayRangeMax());
					System.out.println("BDV says new color    : " + t.getColor());
					setVolumeColorFromMastodon(v);

					//does not work (even with v.setDirty() and company)
					//v.getConverterSetups().get(0).setDisplayRange(t.getDisplayRangeMin(),t.getDisplayRangeMax());

					//does not work (even with v.setDirty() and company)
					//final ConverterSetup cs = pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().getConverterSetup(sac);
					//pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().getBounds().setBounds(cs, new Bounds(t.getDisplayRangeMin(),t.getDisplayRangeMax()));

					//does not work (even with v.setDirty() and company)
					//need to notify of the "repaint request" explicitly because this change is not triggered from
					//SciView itself and hence it does not know about (and does not "repaint" automatically)
					v.setDirty(true);
					v.setNeedsUpdate(true);
					v.setNeedsUpdateWorld(true);
					/*
					*/

					//plan D?
				});

				//optionally: watch when SciView's control panel adjusts the color
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

					/* didn't see this working
					pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().getConverterSetup(
						pluginAppModel.getAppModel().getSharedBdvData().getSources().get(0))
							.setDisplayRange(min,max);
					*/

					final ConverterSetup cs = pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().getConverterSetup(sac);
					pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().getBounds().setBounds(cs, new Bounds(min,max));
					pluginAppModel.getWindowManager().forEachBdvView( view -> view.requestRepaint() );
				});

				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				v.setName(volumeName);
				v.setWantsComposeModel(false); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
				final float scale = 0.5f;      //alternative to v.setPixelToWorldRatio(scale);
				v.setModel( new Matrix4f(scale,0,0,0, 0,-scale,0,0, 0,0,-scale,0, 0,0,0,1) );
				v.setNeedsUpdateWorld(true);
				//now the volume's diagonal in world coords is now:
				//      [0,0,0] -> [scale*origXSize, -scale*origYSize, -scale*origZSize]

				System.out.println("VOlume is from "
					+ v.getBoundingBox().getMin().toString(NumberFormat.getNumberInstance())
					+ " to "
					+ v.getBoundingBox().getMax().toString(NumberFormat.getNumberInstance()) );

				//start the TransferFunction modifying dialog
				getContext().getService(CommandService.class).run(SetTransferFunction.class,true,
						"sciView",sv,"volume",v);

				//--------------------------

				SpatialIndex<Spot> spots = pluginAppModel.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(0);
				float[] pos = new float[3];
				for (Spot spot : spots)
				{
					spot.localize(pos);
					System.out.println("adding 1/2 sphere at "+printArray(pos));

					final Sphere sph = new Sphere(5.0f, 8);
					sph.setName("hooked under Volume");
					sph.setPosition(pos);
					v.addChild(sph);
					sph.setParent(v);
				}

				float[] matrixData = new float[16];
				v.getModel().get(matrixData);
				System.out.println("Volume model matrix: "+printArray(matrixData));
				v.getWorld().get(matrixData);
				System.out.println("Volume world matrix: "+printArray(matrixData));
				v.getModelView().get(matrixData);
				System.out.println("Volume modelView matrix: "+printArray(matrixData));

				//TODO: does not update the scene graph panel, consider using:
				//private EventService eventService;
				//eventService.publish(new NodeAddedEvent(n));
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
		/* DEBUG:
		System.out.println("map[0] = "  +map[0][0]+","+map[1][0]+","+map[2][0]);
		System.out.println("map[80] = " +map[0][80]+","+map[1][80]+","+map[2][80]);
		System.out.println("map[160] = "+map[0][160]+","+map[1][160]+","+map[2][160]);
		System.out.println("map[255] = "+map[0][255]+","+map[1][255]+","+map[2][255]);
		*/
		return map;
	}

	void reportTransform(final Source<?> s)
	{
		final AffineTransform3D t = new AffineTransform3D();
		s.getSourceTransform(0,0,t);

		for (double d : t.getRowPackedCopy()) System.out.print(d+",");
		System.out.println();
	}


	public static void main(String[] args) {
		long[] data = new long[] {3, 5, 6};
		System.out.println("data: "+printArray(data));
	}
}
