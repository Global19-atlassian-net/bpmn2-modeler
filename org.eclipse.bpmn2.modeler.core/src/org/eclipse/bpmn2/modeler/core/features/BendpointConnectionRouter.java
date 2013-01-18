/*******************************************************************************
 * Copyright (c) 2011, 2012 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.core.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.bpmn2.modeler.core.di.DIUtils;
import org.eclipse.bpmn2.modeler.core.features.AbstractConnectionRouter.Direction;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.AnchorLocation;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.BoundaryAnchor;
import org.eclipse.bpmn2.modeler.core.utils.BusinessObjectUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil;
import org.eclipse.bpmn2.modeler.core.utils.ModelUtil;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.graphiti.datatypes.IDimension;
import org.eclipse.graphiti.datatypes.ILocation;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.FixPointAnchor;
import org.eclipse.graphiti.mm.pictograms.FreeFormConnection;
import org.eclipse.graphiti.mm.pictograms.Shape;

/**
 * Router for connections from source to target that can have user-settable bendpoints.
 */
public class BendpointConnectionRouter extends DefaultConnectionRouter {

	// These lists contain the connection's starting points, ending points,
	// and any additional bendpoints that define the segments of the line.
	// This contains the original configuration of the connection
	List<Point> oldPoints = new ArrayList<Point>();
	// This list contains the newly calculated points of the connection
	List<Point> newPoints = new ArrayList<Point>();
	// the original start and end Anchors to which the connection is attached
	Anchor oldStart, oldEnd;
	// the newly calculated start and end Anchors
	Anchor newStart, newEnd;
	// The Connection passed in to route(), cast as a FreeFormConnection for convenience
	FreeFormConnection ffc;

	public BendpointConnectionRouter(IFeatureProvider fp) {
		super(fp);
	}

	@Override
	public boolean route(Connection connection) {
		super.route(connection);
		if (connection instanceof FreeFormConnection)
			ffc = (FreeFormConnection)connection;
		else
			return false;
		
		if (isSelfConnection())
			return routeSelfConnection();
		
		saveOldPoints();
		
		int tries = 0;
		do {
			initialize();
			calculateRoute();
		} while ( ++tries<4 && calculateAnchors() );
		
		// check if anything has changed
		if (pointsChanged()) {
			updateConnection();
			return true;
		}

		return false;
	}
	
	/**
	 * Initialize the newPoints list and set the new start and end anchors
	 * 
	 * @param ffc - the FreeFormConnection
	 */
	protected void initialize() {
		createNewPoints(newStart, newEnd, ffc.getBendpoints());
	}
	
	protected boolean calculateRoute() {
		// this router simply checks if a connection bendpoint is inside a source or target
		// shape and removes it.
		boolean changed = false;

		AnchorContainer source = connection.getStart().getParent();
		AnchorContainer target = connection.getEnd().getParent();
		Point p1;
		for (int i=1; i<newPoints.size()-1; ++i) {
			p1 = newPoints.get(i);
			if (GraphicsUtil.contains((Shape)source,p1) || GraphicsUtil.contains((Shape)target,p1)) {
				newPoints.remove(i);
				changed = true;
			}
		}
		return changed;
	}
	
	/**
	 * Route connections whose source and target are the same. This only reroutes the
	 * connection if there are currently no bendpoints in the connection - we don't
	 * want to reroute a connection that may have already been manually rerouted.
	 * 
	 * @param connection - the connection to be routed
	 * @return true if the router has done any work
	 */
	protected boolean routeSelfConnection() {
		// save the current points of the connection
		saveOldPoints();
		
		if (!isSelfConnection())
			return false;
		
		Point movedBendpoint = getMovedBendpoint(ffc);
		Point addedBendpoint = getAddedBendpoint(ffc);
		if (movedBendpoint==null && addedBendpoint==null) {
			if (ffc.getStart() != ffc.getEnd() && ffc.getBendpoints().size()>0) {
				// this connection starts and ends at the same node but it has different
				// anchor points and at least one bendpoint, which makes it likely that
				// this connection was already routed previously and the self-connection
				// is how the user wants it. But, check if the user wants to force routing.
				if (!forceRouting(ffc))
					return false;
			}
		}
		
		// create the necessary bendpoints so that the connection loops from the node's
		// right edge to the top edge
		AnchorContainer node = connection.getEnd().getParent();
		Map<AnchorLocation, BoundaryAnchor> targetBoundaryAnchors = AnchorUtil.getBoundaryAnchors(node);
		BoundaryAnchor targetTop = targetBoundaryAnchors.get(AnchorLocation.TOP);
		BoundaryAnchor targetRight = targetBoundaryAnchors.get(AnchorLocation.RIGHT);

		// initialize the new connection points list
		createNewPoints(targetRight.anchor, targetTop.anchor, null);
		
		// create the bendpoints that loop the connection around the top-right corner of the figure
		ILocation loc = peService.getLocationRelativeToDiagram((Shape)node);
		IDimension size = GraphicsUtil.calculateSize(node);
		int x1 = loc.getX() + size.getWidth() + 20;
		int y1 = loc.getY() + size.getHeight() / 2;
		int x2 = loc.getX() + size.getWidth() / 2;
		int y2 = loc.getY() - 20;
		Point right = gaService.createPoint(x1, y1); // the point to the right of the node
		Point corner = gaService.createPoint(x1, y2); // point above the top-right corner 
		Point top = gaService.createPoint(x2, y2); // point above the node
		
		// adjust these points to the moved or added bendpoint if possible
		Point p = movedBendpoint != null ? movedBendpoint : addedBendpoint;
		if (p!=null) {
			int x = p.getX();
			int y = p.getY();
			if (x > loc.getX() + size.getWidth() + 2) {
				right.setX(x);
				corner.setX(x);
			}
			if (y < loc.getY() - 2) {
				top.setY(y);
				corner.setY(y);
			}
		}

		// and add them to the new points list
		newPoints.add(1,right);
		newPoints.add(2,corner);
		newPoints.add(3,top);

		if (pointsChanged()) {
			updateConnection();
			return true;
		}

		return false;
	}

	/**
	 * Set a property in the given FreeFormConnection that represents the index of an existing
	 * bendpoint that has been moved by the user. This bendpoint is taken into consideration
	 * in the new routing calculations.
	 * 
	 * @param connection - FreeFormConnection to check
	 * @param index - index of a bendpoint. If this value is out of range, the property will be
	 * remmoved from the connection
	 */
	public static void setMovedBendpoint(Connection connection, int index) {
		setInterestingBendpoint(connection, "moved.", index);
	}

	public static void setAddedBendpoint(Connection connection, int index) {
		setInterestingBendpoint(connection, "added.", index);
	}

	protected static void setInterestingBendpoint(Connection connection, String type, int index) {
		if (connection instanceof FreeFormConnection) {
			int size = ((FreeFormConnection)connection).getBendpoints().size();
			if (index>=0 && size>0) {
				if (index>=size)
					index = size-1;
				AbstractConnectionRouter.setRoutingInfoInt(connection, type+ROUTING_INFO_BENDPOINT, index);
			}
			else
				AbstractConnectionRouter.removeRoutingInfo(connection, type+ROUTING_INFO_BENDPOINT);
		}
	}

	/**
	 * Return the "moved bendpoint" property that was previously set in the FreeFormConnection
	 * by setMovedBendpoint()
	 *  
	 * @param connection - FreeFormConnection to check
	 * @return a Graphiti Point in Diagram-relative coordinates, or null if the property is not set
	 */
	public static Point getMovedBendpoint(Connection connection) {
		return getInterestingBendpoint(connection, "moved.");
	}
	
	public static Point getAddedBendpoint(Connection connection) {
		return getInterestingBendpoint(connection, "added.");
	}
	
	protected static Point getInterestingBendpoint(Connection connection, String type) {
		try {
			int index = AbstractConnectionRouter.getRoutingInfoInt(connection, type+ROUTING_INFO_BENDPOINT);
			return ((FreeFormConnection)connection).getBendpoints().get(index);
		}
		catch (Exception e) {
		}
		return null;
	}

	/**
	 * Save the connection's start/end anchors, and their locations as well as
	 * the connection's bendpoints in an array list. This is used to compare
	 * against the new array of points calculated by the router.
	 */
	protected void saveOldPoints() {
		oldStart = connection.getStart();
		oldEnd = connection.getEnd();
		// set the new start/end anchors to the existing ones - these may change as a result of the new route
		newStart = oldStart;
		newEnd = oldEnd;

		oldPoints.clear();
		oldPoints.add(GraphicsUtil.createPoint(oldStart));
		for (Point p : ffc.getBendpoints()) {
			oldPoints.add(gaService.createPoint(p.getX(), p.getY()));
		}
		oldPoints.add(GraphicsUtil.createPoint(oldEnd));
	}
	
	/**
	 * Saves the given Anchors as the new start/end anchors for the connection, as well
	 * as its existing bendpoints in an array used to calculate the connection's new
	 * routing. If a list of points is given they are inserted into the array between
	 * the starting and ending point.
	 *  
	 * @param newStart - a FixPointAnchor for the connection's new start Anchor
	 * @param newEnd - a FixPointAnchor for the connection's new end Anchor
	 * @param points - an list of points to add to the new array
	 */
	protected void createNewPoints(Anchor newStart, Anchor newEnd, List<Point> points) {
		this.newStart = newStart;
		this.newEnd = newEnd;

		newPoints.clear();
		newPoints.add(GraphicsUtil.createPoint(newStart));
		if (points!=null && points.size()>0)
			newPoints.addAll(points);
		newPoints.add(GraphicsUtil.createPoint(newEnd));
	}

	/**
	 * Compare the connection's original start/end locations and all of its bendpoints
	 * with the newly calculated points.
	 * 
	 * @return true if the connection is different from the newly calculated points
	 */
	protected boolean pointsChanged() {
		if (newPoints.size()==0)
			return false;
		if (oldPoints.size()!=newPoints.size()) {
			return true;
		}
		for (int i=0; i<oldPoints.size(); ++i) {
			Point p1 = oldPoints.get(i);
			Point p2 = newPoints.get(i);
			if (!GraphicsUtil.pointsEqual(p1, p2)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Set the connection's new start/end point anchors and the newly calculated bendpoints.
	 */
	protected void updateConnection() {
		ffc.getBendpoints().clear();
		
		ffc.setStart(newStart);
		Point p1 = newPoints.get(0);
		for (int i=1; i<newPoints.size()-1; ++i) {
			Point p2 = newPoints.get(i);
			if (i+1 < newPoints.size()) {
				// remove unnecessary bendpoints: two consecutive
				// horizontal or vertical line segments
				Point p3 = newPoints.get(i+1);
				if ((isVertical(p1,p2) && isVertical(p2,p3)) ||
						(isHorizontal(p1,p2) && isHorizontal(p2,p3))) {
					continue;
				}
			}
			ffc.getBendpoints().add( GraphicsUtil.createPoint(p2.getX(), p2.getY()));
			p1 = p2;
		}
		ffc.setEnd(newEnd);
		
		DIUtils.updateDIEdge(ffc);
	}
	
	/**
	 * Given a new list of bendpoints for our FreeFormConnection, calculate the direction (UP, DOWN, LEFT or RIGHT)
	 * from at a given bendpoint that is orthogonal to the line segment before the bendpoint. If the given bendpoint
	 * is at the beginning or end of the list, use the orientation of the source/target figure to determine an
	 * orthogonal direction.
	 *  
	 * @param index - index into the newPoints list of the point to check
	 * @return - a Direction: UP, DOWN, LEFT or RIGHT
	 * 
	 * @throws Exception if an orthogonal can not be determined.
	 */
	protected final Direction getDirection(int index) throws Exception {
		Point p1 = newPoints.get(index);
		
		if (index==0) {
			Anchor a = ffc.getStart();
			AnchorContainer source = a.getParent();
			if (AnchorUtil.isBoundaryAnchor(a)) {
				BoundaryAnchor ba = AnchorUtil.findNearestBoundaryAnchor(source, p1);
				switch (ba.locationType) {
				case TOP:
					return Direction.DOWN;
				case BOTTOM:
					return Direction.UP;
				case LEFT:
					return Direction.RIGHT;
				case RIGHT:
					return Direction.LEFT;
				}
			}
			else {
				switch (AnchorUtil.findNearestEdge((Shape)source, p1)) {
				case TOP:
					return Direction.UP;
				case BOTTOM:
					return Direction.DOWN;
				case LEFT:
					return Direction.LEFT;
				case RIGHT:
					return Direction.RIGHT;
				}
			}
		}
		
		if (index==newPoints.size()-1) {
			Anchor a = ffc.getEnd();
			AnchorContainer target = a.getParent();
			if (AnchorUtil.isBoundaryAnchor(a)) {
				BoundaryAnchor ba = AnchorUtil.findNearestBoundaryAnchor(target, p1);
				switch (ba.locationType) {
				case TOP:
					return Direction.DOWN;
				case BOTTOM:
					return Direction.UP;
				case LEFT:
					return Direction.RIGHT;
				case RIGHT:
					return Direction.LEFT;
				}
			}
			else {
				switch (AnchorUtil.findNearestEdge((Shape)target, p1)) {
				case TOP:
					return Direction.DOWN;
				case BOTTOM:
					return Direction.UP;
				case LEFT:
					return Direction.RIGHT;
				case RIGHT:
					return Direction.LEFT;
				}
			}
		}
		
		Point p2 = newPoints.get(index+1);
		Point p0 = newPoints.get(index-1);
		
		// [p0,p1] should form either a vertical or horizontal line because a
		// bendpoint was already added to ensure this condition. The new direction
		// should be orthogonal to [p0,p1] so we just need to determine positive or negative.
		if (isHorizontal(p0, p1)) {
			if (p2.getY() > p1.getY())
				return Direction.DOWN;
			else
				return Direction.UP;
		}
		else if (isVertical(p0,p1)) {
			if (p2.getX() > p1.getX())
				return Direction.RIGHT;
			else
				return Direction.LEFT;
		}
		if (index>0)
			return getDirection(index-1);
		throw new Exception("getDirection: consecutive points are not orthogonal");
	}

	/**
	 * Check if the given connection's bendpoints are identical to the list of given points.
	 * This will also return TRUE if the connection's bendpoints list is in reverse order.
	 * 
	 * @param bp1 - a list of Points to compare against
	 * @param c - the FreeFormConnection whose bendpoints are to be tested for equality
	 * @return true if the connection's bendpoints is identical to the list of points in bp1
	 */
	protected final boolean bendpointsEqual(List<Point> bp1, Connection c) {
		if (!(c instanceof FreeFormConnection))
			return false;
		
		FreeFormConnection ffc2 = (FreeFormConnection)c;
		List<Point> bp2 = new ArrayList<Point>();
		bp2.add(GraphicsUtil.createPoint(ffc2.getStart()));
		bp2.addAll(ffc2.getBendpoints());
		bp2.add(GraphicsUtil.createPoint(ffc2.getEnd()));
		int size = bp1.size();
		if (bp2.size() != size)
			return false;
	
		int reverse = -1;
		Point p1 = bp1.get(0);
		Point p2 = bp2.get(0);
		if (GraphicsUtil.pointsEqual(p1,p2)) {
			reverse = 0;
		}
		else {
			// the connections may still be stacked on top of each other,
			// but their bendpoints may simply be reversed, i.e. their
			// directions are reversed
			p2 = bp2.get(size-1);
			if (GraphicsUtil.pointsEqual(p1,p2)) {
				reverse = size-1;
			}
		}
		if (reverse>=0 ) {
			for (int i=0; i<size; ++i) {
				p1 = bp1.get(i);
				if (reverse==0)
					p2 = bp2.get(i);
				else
					p2 = bp2.get(reverse-i);
				if (!GraphicsUtil.pointsEqual(p1,p2))
					return false;
			}
		}
		return true;
	}

	/**
	 * Check the newly calculated set of bendpoints for the given FreeFormConnection to see
	 * if they overlap with any other connections that are connected to the same endpoints.
	 * If this connection is stacked on top of another one, adjust the new bendpoints so they
	 * are offset horizontally and vertically so they can be easily be distinguished.
	 * 
	 * @return true if the newPoints bendpoints array was changed
	 */
	protected boolean offsetStackedConnections() {
		int offset = 0;
		Anchor sourceAnchor = ffc.getStart();
		List<Connection> connections = new ArrayList<Connection>();
		connections.addAll(sourceAnchor.getOutgoingConnections());
		connections.addAll(sourceAnchor.getIncomingConnections());
		for (Connection c : connections) {
			if (c!=ffc) {
				if (bendpointsEqual(newPoints,c)) {
					// here's one
					offset += 10;
				}
			}
		}
		
		if (offset!=0) {
			try {
				Direction d1 = getDirection(0);
				offsetBendpoints(d1, offset);
				return true;
			}
			catch (Exception e) {
			}
		}
		return false;
	}
	
	/**
	 * Offset the calculated list of bendpoints by the given amount and insert new
	 * bendpoints to shift the entire connection horizontally and vertically.
	 * 
	 * @param d - the shift direction
	 * @param offset - the shift amount
	 */
	protected void offsetBendpoints(Direction d, int offset) {
		Point p0 = newPoints.get(0);
		Point p1 = GraphicsUtil.createPoint(newPoints.get(1));
		Point p2 = GraphicsUtil.createPoint(newPoints.get( newPoints.size()-2 ));
		Point p3 = newPoints.get( newPoints.size()-1 );
		Point p;
		int x, y;
		
		// offset all of the intermediate points first
		for (int i=1; i<newPoints.size()-1; ++i) {
			p = newPoints.get(i);
			switch (d) {
			case UP:
			case DOWN:
				p.setX(p.getX() + offset);
				p.setY(p.getY() + offset);
				break;
			case LEFT:
			case RIGHT:
				p.setX(p.getX() - offset);
				p.setY(p.getY() + offset);
				break;
			}
		}
		switch (d) {
		case UP:
		case DOWN:
			// offset is horizontal
			x = p0.getX();
			y = p0.getY() + (p1.getY() - p0.getY()) / 3; 
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(1,p);
			x += offset;
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(2,p);
			x = p3.getX();
			y = p3.getY() + (p2.getY() - p3.getY()) / 3; 
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(newPoints.size()-1,p);
			x += offset;
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(newPoints.size()-2,p);
			break;
		case LEFT:
		case RIGHT:
			// offset is vertical
			x = p0.getX() + (p1.getX() - p0.getX()) / 3;
			y = p0.getY(); 
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(1,p);
			y += offset;
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(2,p);
			x = p3.getX() + (p2.getX() - p3.getX()) / 3;
			y = p3.getY(); 
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(newPoints.size()-1,p);
			y += offset;
			p = GraphicsUtil.createPoint(x, y);
			newPoints.add(newPoints.size()-2,p);
			break;
		}
		
	}

	protected boolean calculateAnchors() {
	
		boolean changed = false;
		AnchorContainer source = connection.getStart().getParent();
		AnchorContainer target = connection.getEnd().getParent();
		int newSize = newPoints.size();
		int minDist = 10;
	
		if (AnchorUtil.isBoundaryAnchor(oldStart)) {
			// and the start anchor is a BoundaryAnchor:
			// find the BoundaryAnchor that is nearest to the new first bendpoint
			Point p1 = newPoints.get(1);
			if (newSize>2) {
				// some heuristics here:
				// if the distance between this bendpoint and the start anchor
				// location is less than <mumble>, try the next bendpoint
				Point p2 = newPoints.get(0);
				if (GraphicsUtil.getLength(p1, p2)<=minDist)
					p1 = newPoints.get(2);
			}
			FixPointAnchor a = AnchorUtil.findNearestAnchor(source, p1);
			if (a!=oldStart) {
				// the start boundary anchor needs to change
				oldStart = newStart = a;
				changed = true;
			}
		}
		
		if (AnchorUtil.isBoundaryAnchor(oldEnd)) {
			// the end anchor is a BoundaryAnchor too:
			// find the BoundaryAnchor that is nearest to the new last bendpoint
			Point p1 = newPoints.get(newSize-2);
			if (newSize>2) {
				Point p2 = newPoints.get(newSize-1);
				if (GraphicsUtil.getLength(p1, p2)<=minDist)
					p1 = newPoints.get(newSize-3);
			}
			FixPointAnchor a = AnchorUtil.findNearestAnchor(target, p1);
			if (a!=oldEnd) {
				// the start boundary anchor needs to change
				oldEnd = newEnd = a;
				changed = true;
			}
		}
	
		return changed;
	}
}