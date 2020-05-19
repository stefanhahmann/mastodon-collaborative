package org.mastodon.tomancak;

import bdv.viewer.Source;
import graphics.scenery.Node;
import graphics.scenery.Origin;
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
import sc.iview.vector.FloatVector3;

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

				//image transform -- this is what I should actually be considering when scaling the
				//volume.. the fixVolumeScale() method should take it!
				AffineTransform3D imgTransform = new AffineTransform3D();
				s.getSourceTransform(0,0, imgTransform);
				System.out.println("ImgTransform:"+printArray(imgTransform.getRowPackedCopy()));

				//crank up the volume :-)
				final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac, volumeName);

				setVolumeColorFromMastodon(v);
				//may setup own display range if need-be

				//adjust the transfer function to a "diagonal"
				TransferFunction tf = v.getTransferFunction();
				tf.clear();
				tf.addControlPoint(0.0f, 0.0f);
				//TODO: try to raise the middle point by 20%
				tf.addControlPoint(1.0f, 1.0f);


				//optionally: watch when SciView's control panel adjusts the color
				//and re-reset it back to that of Mastodon
				v.getConverterSetups().get(0).setupChangeListeners().add( (t) -> {
					System.out.println("display range: "+
							v.getConverterSetups().get(0).getDisplayRangeMin()+" -> "+
							v.getConverterSetups().get(0).getDisplayRangeMax() );
					System.out.println( "changed color to "+t.getColor() );
					//
					//be of the current Mastodon's color
					setVolumeColorFromMastodon(v);
				});

				v.setName(volumeName);
				fixVolumeScale(v,voxelDims);
				v.setOrigin(Origin.FrontBottomLeft);
				v.setDirty(true);
				v.setNeedsUpdate(true);

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

	void fixVolumeScale(final Volume v, final float[] voxelDims)
	{
		float min = voxelDims[0];
		for (int i = 1; i < voxelDims.length; ++i) min = Math.min( voxelDims[i], min );
		//TODO: what if min > 1.0?
		for (int i = 0; i < voxelDims.length; ++i) voxelDims[i] /= min;

		//this will rotate (wanted only y-axis flip) volume so that axes would match those of Mastodon's BDV
		voxelDims[1] *= -1f;
		voxelDims[2] *= -1f;

		v.getScale().set(new Vector3f(voxelDims));
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
