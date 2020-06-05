package org.mastodon.tomancak;

import cleargl.GLVector;
import graphics.scenery.Cylinder;
import graphics.scenery.Node;

public class DisplayCompassAxes
{
	private Node[]   axesData = null;
	private boolean axesShown = false;

	DisplayCompassAxes() { this(true); }
	DisplayCompassAxes(boolean alreadyCreateAndShowAxes)
	{
		if (alreadyCreateAndShowAxes)
		{
		    CreateDisplayAxes();
		    ToggleDisplayAxes();
		}
	}

	//returns the "gathering node" of the axes
	public
	Node CreateDisplayAxes()
	{
		//remove any old axes, if they exist at all...
		RemoveDisplayAxes();

		final float barRadius = 1.0f;
		final float barLength = 30.0f;

		axesData = new Node[] {
			new Node("Scene orientation compass"),
			new Cylinder(barRadius,barLength,4),
			new Cylinder(barRadius,barLength,4),
			new Cylinder(barRadius,barLength,4)};

		axesData[0].setVisible(false);

		//set material - color
		//NB: RGB colors ~ XYZ axes
		axesData[1].getMaterial().getDiffuse().set(1f,0f,0f);
		axesData[2].getMaterial().getDiffuse().set(0f,1f,0f);
		axesData[3].getMaterial().getDiffuse().set(0f,0f,1f);

		axesData[1].setName("compass axis: X");
		axesData[2].setName("compass axis: Y");
		axesData[3].setName("compass axis: Z");

		//set orientation for x,z axes
		ReOrientNode(axesData[1],defaultNormalizedUpVector,new GLVector(1.0f,0.0f,0.0f));
		ReOrientNode(axesData[3],defaultNormalizedUpVector,new GLVector(0.0f,0.0f,1.0f));

		for (int i=1; i < 4; ++i)
			axesData[0].addChild(axesData[i]);

		axesShown = false;
		return axesData[0];
	}

	public
	void RemoveDisplayAxes()
	{
		if (axesData == null) return;

		for (int i=1; i < axesData.length; ++i) axesData[0].removeChild(axesData[i]);

		axesData = null;
		axesShown = false;
	}

	public
	Node getGatheringNode()
	{ return axesData != null ? axesData[0] : null; }

	public
	boolean ToggleDisplayAxes()
	{
		//first run, init the data
		if (axesData == null)
		{
			System.out.println("Creating compass axes before turning them on...");
			CreateDisplayAxes();
		}

		//toggle the flag
		axesShown ^= true;

		//adjust the visibility
		for (Node n : axesData)
			n.setVisible(axesShown);

		return axesShown;
	}

	public
	boolean IsSceneAxesVisible()
	{ return axesShown; }
	//----------------------------------------------------------------------------


	/** Rotates the node such that its orientation (whatever it is for the node, e.g.
	    the axis of rotational symmetry in a cylinder) given with _normalized_
	    currentNormalizedOrientVec will match the new orientation newOrientVec. */
	public
	void ReOrientNode(final Node node, final GLVector currentNormalizedOrientVec,
	                  final GLVector newOrientVec)
	{
		//plan: vector/cross product of the initial object's orientation and the new orientation,
		//and rotate by angle that is taken from the scalar product of the two

		//the rotate angle
		final float rotAngle = (float)Math.acos(currentNormalizedOrientVec.times(newOrientVec.getNormalized()));

		//for now, the second vector for the cross product
		GLVector tmpVec = newOrientVec;

		//two special cases when the two orientations are (nearly) colinear:
		//
		//a) the same direction -> nothing to do (don't even update the currentNormalizedOrientVec)
		if (Math.abs(rotAngle) < 0.01f) return;
		//
		//b) the opposite direction -> need to "flip"
		if (Math.abs(rotAngle-Math.PI) < 0.01f)
		{
			//define non-colinear helping vector, e.g. take a perpendicular one
			tmpVec = new GLVector(-newOrientVec.y(), newOrientVec.x(), 0.0f);
		}

		//axis along which to perform the rotation
		tmpVec = currentNormalizedOrientVec.cross(tmpVec).normalize();
		node.getRotation().rotateAxis(rotAngle, tmpVec.x(),tmpVec.y(),tmpVec.z());

		//System.out.println("rot axis=("+tmpVec.x()+","+tmpVec.y()+","+tmpVec.z()
		//                   +"), rot angle="+rotAngle+" rad");
	}

	/** Calls the ReOrientNode() before the normalized variant of newOrientVec
	    will be stored into the currentNormalizedOrientVec. */
	public
	void ReOrientNodeAndSaveNewNormalizedOrientation(final Node node,
	                  final GLVector currentNormalizedOrientVec,
	                  final GLVector newOrientVec)
	{
		ReOrientNode(node, currentNormalizedOrientVec, newOrientVec);

		//update the current orientation
		currentNormalizedOrientVec.minusAssign(currentNormalizedOrientVec);
		currentNormalizedOrientVec.plusAssign(newOrientVec);
		currentNormalizedOrientVec.normalize();
	}

	/** fixed reference "up" vector used mainly in conjunction with ReOrientNode() */
	final GLVector defaultNormalizedUpVector = new GLVector(0.0f,1.0f,0.0f);
}
