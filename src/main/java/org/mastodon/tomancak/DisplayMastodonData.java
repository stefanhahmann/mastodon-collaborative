package org.mastodon.tomancak;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.*;
import graphics.scenery.Material;
import graphics.scenery.Node;
import graphics.scenery.Sphere;
import graphics.scenery.volumes.TransferFunction;
import graphics.scenery.volumes.Volume;
import net.imglib2.RandomAccessibleInterval;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.MamutViewBdv;

import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.coloring.GraphColorGenerator;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.tomancak.dialogs.SpotsDisplayParamsDialog;
import org.scijava.Context;
import org.scijava.command.CommandService;
import sc.iview.SciView;
import org.scijava.event.EventService;
import sc.iview.commands.edit.AddOrientationCompass;
import sc.iview.commands.view.SetTransferFunction;
import sc.iview.event.NodeChangedEvent;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class DisplayMastodonData
{
	//Mastodon connection
	final MastodonPluginAppModel pluginAppModel;
	final FocusedBdvWindow controllingBdvWindow = new FocusedBdvWindow();

	//the overall coordinate scale factor from Mastodon to SciView coords
	//NB: the smaller scale the better! with scale 1, pixels look terrible....
	public static
	final float scale = 0.01f;

	//SciView connection + EventService that is used to update SciView's inspector panel
	SciView sv = null;
	EventService events = null;

	//shared cache of colormaps for volumes (to prevent that they are re-created over and over again)
	final CachedColorTables volumeColormaps = new CachedColorTables();

	public
	DisplayMastodonData(final MastodonPluginAppModel pluginAppModel)
	{
		this(pluginAppModel,false);
	}

	public
	DisplayMastodonData(final MastodonPluginAppModel pluginAppModel, final boolean startSciView)
	{
		this.pluginAppModel = pluginAppModel;
		if (startSciView) startSciView();
	}

	//aid class to find out if we are started from some BDV window
	class FocusedBdvWindow
	{
		MamutViewBdv focusedBdvWindow = null;

		boolean isThereSome() { return focusedBdvWindow != null; }
		MamutViewBdv get() { return focusedBdvWindow; }

		void setPossiblyTo(MamutViewBdv adept)
		{ if (adept.getFrame().isFocused()) focusedBdvWindow = adept; }

		void setupFrom(final MastodonPluginAppModel mastodon)
		{
			focusedBdvWindow = null;
			mastodon.getWindowManager().forEachBdvView( bdvView -> setPossiblyTo(bdvView) );

			if (focusedBdvWindow != null)
				System.out.println("Controlling window found: "+focusedBdvWindow.getFrame().getTitle());
			else
				System.out.println("No controlling window found");
		}
	}

	//returns if SciView has been started sucessfully
	public
	boolean startSciView()
	{
		new Thread("Mastodon's SciView")
		{
			@Override
			public void run()
			{
				controllingBdvWindow.setupFrom(pluginAppModel);

				try {
					sv = SciView.create();
					sv.setInterpreterWindowVisibility(false);
					Thread.sleep(2000); //a bit of a grace time before we continue
					System.out.println("SciView started...");
				} catch (Exception e) {
					System.out.println("SciView has a problem:");
					e.printStackTrace();
					sv = null;
					events = null;
				}

				if (sv != null)
				{
					//find the event service to be able to notify the inspector
					events = sv.getScijavaContext().getService(EventService.class);
					System.out.println("Found an event service: "+events);
				}
			}
		};

		return sv != null;
	}

	// ============================================================================================

	public static
	String volumeName = "Mastodon's raw data";

	public Volume showOneTimePoint(final int timepoint)
	{
		return showOneTimePoint(timepoint,pluginAppModel,sv);
	}

	public static
	Volume showOneTimePoint(final int timepoint, final MastodonPluginAppModel mastodonPlugin, final SciView sv)
	{
		final SourceAndConverter<?> sac = mastodonPlugin.getAppModel().getSharedBdvData().getSources().get(0);
		final Volume v = (Volume)sv.addVolume((RandomAccessibleInterval)sac.getSpimSource().getSource(timepoint,0), volumeName);

		//adjust the transfer function to a "diagonal"
		setTransferFunction(v);

		//override SciView's initial LUT
		final CachedColorTables volumeColormaps = new CachedColorTables();
		restoreVolumeColor(v,volumeColormaps);

		//initial min-max display range comes from BDV
		final ConverterSetup cs = mastodonPlugin.getAppModel().getSharedBdvData().getConverterSetups().getConverterSetup(sac);
		v.getConverterSetups().get(0).setDisplayRange(cs.getDisplayRangeMin(), cs.getDisplayRangeMax());

		//prepare per axis scaling factors to maintain the data voxel ratio
		final double[] voxelDims = new double[3];
		calculateDisplayVoxelRatioAlaBDV(voxelDims, sac.getSpimSource());
		System.out.println("scaling: "+voxelDims[0]+" x "+voxelDims[1]+" x "+voxelDims[2]);

		v.setName(volumeName);
		v.removeChild( v.getChildren().get(0) ); //removes the grid node

		v.setWantsComposeModel(false); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
		v.setModel( new Matrix4f(scale*(float)voxelDims[0],0,0,0,
		                         0,-scale*(float)voxelDims[1],0,0,
		                         0,0,-scale*(float)voxelDims[2],0,
		                         0,0,0,1) );
		v.setNeedsUpdateWorld(true);
		//now the volume's diagonal in world coords is now:
		//      [0,0,0] -> [scale*origXSize, -scale*origYSize, -scale*origZSize]

		v.getViewerState().setInterpolation(Interpolation.NLINEAR);
		v.getVolumeManager().requestRepaint();
		return v;
	}

	// ============================================================================================

	public Volume showTimeSeries()
	{
		return showTimeSeries(pluginAppModel,sv);
	}

	public static
	Volume showTimeSeries(final MastodonPluginAppModel mastodonPlugin, final SciView sv)
	{
		final SourceAndConverter<?> sac = mastodonPlugin.getAppModel().getSharedBdvData().getSources().get(0);
		final Volume v = (Volume)sv.addVolume((SourceAndConverter)sac,
				mastodonPlugin.getAppModel().getSharedBdvData().getNumTimepoints(), volumeName);

		//adjust the transfer function to a "diagonal"
		setTransferFunction(v);

		//override SciView's initial LUT
		final CachedColorTables volumeColormaps = new CachedColorTables();
		restoreVolumeColor(v,volumeColormaps);

		//initial min-max display range comes from BDV
		//... comes from BDV transiently since we're using its data directly ...

		//prepare per axis scaling factors to maintain the data voxel ratio
		//... isotropy scaling is taken care of in the BDV data too ...

		v.setName(volumeName);
		v.removeChild( v.getChildren().get(0) ); //removes the grid node

		v.setWantsComposeModel(false); //makes position,scale,rotation be ignored, also pxToWrld scale is ignored
		v.setModel( new Matrix4f(scale,0,0,0,
		                         0,-scale,0,0,
		                         0,0,-scale,0,
		                         0,0,0,1) );
		v.setNeedsUpdateWorld(true);
		//now the volume's diagonal in world coords is now:
		//      [0,0,0] -> [scale*origXSize, -scale*origYSize, -scale*origZSize]

		v.getViewerState().setInterpolation(Interpolation.NLINEAR);
		v.getVolumeManager().requestRepaint();

		//this block needs to be here to re-assure the correct (that of BDV) color for the volume
		v.getViewerState().getState().changeListeners().add(new ViewerStateChangeListener() {
			@Override
			public void viewerStateChanged(ViewerStateChange viewerStateChange) {
				final int TP = v.getViewerState().getCurrentTimepoint();
				System.out.println("SciView says new timepoint "+TP);

				//also keep ignoring the SciView's color/LUT and enforce color from BDV
				restoreVolumeColor(v,volumeColormaps);
			}
		});

		return v;
	}

	public
	void makeSciViewReadBdvSetting(final Volume v)
	{
		//watch when BDV (through its color&brightness dialog) changes display range or volume's color
		pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups().listeners().add( t -> {
			System.out.println("BDV says display range: " + t.getDisplayRangeMin() + " -> " + t.getDisplayRangeMax());
			System.out.println("BDV says new color    : " + t.getColor());

			//request that the volume be repainted in SciView
			restoreVolumeColor(v,volumeColormaps);
			v.getVolumeManager().requestRepaint();

			//also notify the inspector panel
			events.publish(new NodeChangedEvent(v));
		});

		//this block may set up a listener for TP change in a BDV from which SciView was started, if any...
		if (controllingBdvWindow.isThereSome())
		{
			System.out.println("Will be syncing timepoints with "+controllingBdvWindow.get().getFrame().getTitle());
			controllingBdvWindow.get().getViewerPanelMamut().addTimePointListener(tp -> {
					System.out.println("BDV says new timepoint "+tp);
					v.getViewerState().setCurrentTimepoint(tp);
					v.getVolumeManager().requestRepaint();

					//also notify the inspector panel
					events.publish(new NodeChangedEvent(v));
				});
		}
		else System.out.println("Will NOT be syncing timepoints with any BDV window");
	}

	public
	void makeBdvReadSciViewSetting(final Volume v)
	{
		//watch when SciView's inspector panel adjusts the color
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
			restoreVolumeColor(v,volumeColormaps);

			//final ConverterSetups setups = pluginAppModel.getAppModel().getSharedBdvData().getConverterSetups();
			//setups.getBounds().setBounds( setups.getConverterSetup(sac), new Bounds(min,max) );
			pluginAppModel.getWindowManager().forEachBdvView( view -> view.requestRepaint() );
		});
	}

	// ============================================================================================

	public
	float spotRadius = 0.1f;

	public
	final SpotsDisplayParamsDialog.ParamsWrapper spotVizuParams = new SpotsDisplayParamsDialog.ParamsWrapper();

	public
	void showSpots(final int timepoint, final Node underThisNode)
	{
		showSpots(timepoint,underThisNode,null);
	}

	public
	void showSpots(final int timepoint, final Node underThisNode, final GraphColorGenerator<Spot, Link> colorGenerator)
	{
		SpatialIndex<Spot> spots = pluginAppModel.getAppModel().getModel().getSpatioTemporalIndex().getSpatialIndex(timepoint);
		final Vector3f hubPos = underThisNode.getPosition();

		//list of existing nodes that shall be updated
		if (underThisNode.getChildren().size() > spots.size())
		{
			//removing spots from the children list is somewhat funky,
			//we better remove all and add all anew
			underThisNode.getChildren().removeIf(f -> true);
		}
		Iterator<Node> existingNodes = underThisNode.getChildren().iterator();

		//list of new nodes beyond the existing nodes, we better add at the very end
		//to make sure the iterator can remain consistent
		List<Node> extraNodes = new LinkedList<>();

		//reference vector with diffuse color from the gathering node
		//NB: relying on the fact that Scenery keeps only a reference (does not make own copies)
		final Material sharedMaterialObj = underThisNode.getMaterial();

		for (Spot spot : spots)
		{
			//find a Sphere to represent this spot
			Sphere sph;
			if (existingNodes.hasNext())
			{
				//update existing one
				sph = (Sphere)existingNodes.next();
			}
			else
			{
				//create a new one
				sph = new Sphere(spotRadius, 8);
				sph.getScale().set(spotVizuParams.sphereSize,spotVizuParams.sphereSize,spotVizuParams.sphereSize);
				extraNodes.add(sph);
			}

			//setup the spot
			spot.localize(pos);
			pos[0] = +scale *pos[0] -hubPos.x; //adjust coords to the current volume scale
			pos[1] = -scale *pos[1] -hubPos.y;
			pos[2] = -scale *pos[2] -hubPos.z;
			sph.setPosition(pos);

			if (colorGenerator != null)
			{
				int rgbInt = colorGenerator.color(spot);
				if ((rgbInt&0x00FFFFFF) > 0)
				{
					//before we set the color from the colorGenerator,
					//we have to have for sure own material object (not the shared one)
					if (sph.getMaterial() == sharedMaterialObj) sph.setMaterial(new Material());
					Vector3f rgb = sph.getMaterial().getDiffuse();
					rgb.x = (float)((rgbInt >> 16) & 0xFF) / 255f;
					rgb.y = (float)((rgbInt >>  8) & 0xFF) / 255f;
					rgb.z = (float)((rgbInt      ) & 0xFF) / 255f;
				}
				else
				{
					//share the same color settings of the gathering node (this allows user to choose once and "set" for all)
					//NB: relying on the fact that Scenery keeps only a reference (does not make own copy)
					sph.setMaterial(sharedMaterialObj);
				}
			}
			else
			{
				//share the same color settings of the gathering node (this allows user to choose once and "set" for all)
				//NB: relying on the fact that Scenery keeps only a reference (does not make own copy)
				sph.setMaterial(sharedMaterialObj);
			}

			sph.setName(spot.getLabel());
		}

		//register the extra new spots
		for (Node s : extraNodes) underThisNode.addChild(s);

		//notify the inspector to update the hub node
		underThisNode.setName("Mastodon spots at "+timepoint);
		underThisNode.updateWorld(true,true);
		events.publish(new NodeChangedEvent(underThisNode));
	}

	public
	void updateSpotPosition(final Node spotsGatheringNode, final Spot updatedSpot)
	{
		final Node spotNode = sv.find(updatedSpot.getLabel()); //KILLER! TODO
		if (spotNode != null)
		{
			final Vector3f hubPos = spotsGatheringNode.getPosition();
			updatedSpot.localize(pos);
			pos[0] = +scale *pos[0] -hubPos.x; //adjust coords to the current volume scale
			pos[1] = -scale *pos[1] -hubPos.y;
			pos[2] = -scale *pos[2] -hubPos.z;
			spotNode.setPosition(pos);
			spotNode.setNeedsUpdate(true);
		}
	}

	//aux array to aid transferring of float positions (and avoid re-allocating it)
	final float[] pos = new float[3];

	// ============================================================================================

	static
	void setTransferFunction(final Volume v)
	{
		final TransferFunction tf = v.getTransferFunction();
		tf.clear();
		tf.addControlPoint(0.00f, 0.0f);
		tf.addControlPoint(0.05f, 0.1f);
		tf.addControlPoint(0.90f, 0.7f);
		tf.addControlPoint(1.00f, 0.8f);
	}

	static
	void restoreVolumeColor(final Volume v, final CachedColorTables colormapsCache)
	{
		int rgba = v.getConverterSetups().get(0).getColor().get();
		v.setColormap( colormapsCache.getColormap(rgba) );
	}

	public
	void centerNodeOnVolume(final Node n, final Volume v)
	{
		//short cut to the Source of this Volume
		final Source<?> volumeAsSource = v.getViewerState().getSources().get(0).getSpimSource();

		//image size in number of pixels per axis/dimension
		final long[] dims = new long[3];
		volumeAsSource.getSource(0,0).dimensions(dims);

		//pixel size in units of the smallest-pixel-size
		final double[] ratios = new double[3];
		calculateDisplayVoxelRatioAlaBDV(ratios, volumeAsSource);

		n.setPosition(new double[] { 0.5*scale*dims[0]*ratios[0], -0.5*scale*dims[1]*ratios[1], -0.5*scale*dims[2]*ratios[2] });
	}

	public static
	void calculateDisplayVoxelRatioAlaBDV(final double[] vxAxisRatio, final Source<?> forThisSource)
	{
		forThisSource.getVoxelDimensions().dimensions(vxAxisRatio);

		double minLength = vxAxisRatio[0];
		for (int i = 1; i < vxAxisRatio.length; ++i) minLength = Math.min( vxAxisRatio[i], minLength );
		for (int i = 0; i < vxAxisRatio.length; ++i) vxAxisRatio[i] /= minLength;
	}

	// ============================================================================================

	public static
	void showTransferFunctionDialog(final Context ctx, final Volume v)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SetTransferFunction.class,true,
				"sciView",v.getHub().getApplication(),
				//NB: luckily, getApplication() returns SciView instance
				"volume",v);
	}

	public static
	void showSpotsDisplayParamsDialog(final Context ctx, final Node spots,
	                                  final SpotsDisplayParamsDialog.ParamsWrapper vizuParams)
	{
		//start the TransferFunction modifying dialog
		ctx.getService(CommandService.class).run(SpotsDisplayParamsDialog.class,true,
				"params",vizuParams, "spheresGatheringNode",spots,
				"sphereAlpha",1.0f);
	}

	public static
	void showCompassAxes(final DisplayMastodonData dmd, final Vector3f atThisCenter)
	{
		dmd.sv.getScijavaContext()
			.getService(CommandService.class)
			.run(AddOrientationCompass.class,true,
				"sciView",dmd.sv,
				"axisLength",0.1f, "axisBarRadius",0.001f,
				"attachToCam",true, "showInTheScene",false);

		//NB: we're not displaying axes-compass in the data, so we don't use this now
		//compassMainNode.setPosition(atThisCenter);
		//compassMainNode.getScale().set(scale,-scale,-scale);
	}
}
