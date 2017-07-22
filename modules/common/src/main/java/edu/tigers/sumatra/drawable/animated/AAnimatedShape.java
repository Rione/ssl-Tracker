/*
 * Copyright (c) 2009 - 2017, DHBW Mannheim - Tigers Mannheim
 */
package edu.tigers.sumatra.drawable.animated;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

import com.sleepycat.persist.model.Persistent;

import edu.tigers.sumatra.drawable.IDrawableShape;
import edu.tigers.sumatra.drawable.IDrawableTool;
import edu.tigers.sumatra.math.vector.AVector2;
import edu.tigers.sumatra.math.vector.IVector2;


/**
 * All animated shapes have a center position, line color, fill color, and stroke width.
 * 
 * @author AndreR <andre@ryll.cc>
 */
@Persistent
public class AAnimatedShape implements IDrawableShape
{
	private IVector2 center;
	private IColorAnimator lineColor;
	private IColorAnimator fillColor;
	private float strokeWidth = 10;
	private transient Stroke stroke;
	
	
	@SuppressWarnings("unused")
	protected AAnimatedShape()
	{
		center = AVector2.ZERO_VECTOR;
		lineColor = new ColorAnimatorFixed(Color.BLACK);
	}
	
	
	protected AAnimatedShape(final IVector2 center)
	{
		this.center = center;
		lineColor = new ColorAnimatorFixed(Color.BLACK);
	}
	
	
	protected AAnimatedShape(final IVector2 center, final IColorAnimator lineColor, final IColorAnimator fillColor)
	{
		this.center = center;
		this.lineColor = lineColor;
		this.fillColor = fillColor;
	}
	
	
	@Override
	public void paintShape(final Graphics2D g, final IDrawableTool tool, final boolean invert)
	{
		if (stroke == null)
		{
			stroke = new BasicStroke(tool.scaleXLength(strokeWidth));
		}
		g.setStroke(stroke);
		
		g.setColor(lineColor.getColor());
	}
	
	
	@Override
	public void setStrokeWidth(final double strokeWidth)
	{
		this.strokeWidth = (float) strokeWidth;
	}
	
	
	/**
	 * @param strokeWidth
	 * @return
	 */
	public AAnimatedShape withStrokeWidth(final double strokeWidth)
	{
		this.strokeWidth = (float) strokeWidth;
		return this;
	}
	
	
	@Override
	public void setColor(final Color color)
	{
		lineColor = new ColorAnimatorFixed(color);
	}
	
	
	/**
	 * @param center the center to set
	 * @return
	 */
	public AAnimatedShape withCenter(final IVector2 center)
	{
		this.center = center;
		return this;
	}
	
	
	/**
	 * @param lineColor the lineColor to set
	 * @return
	 */
	public AAnimatedShape withLineColor(final IColorAnimator lineColor)
	{
		this.lineColor = lineColor;
		return this;
	}
	
	
	/**
	 * @param fillColor the fillColor to set
	 * @return
	 */
	public AAnimatedShape withFillColor(final IColorAnimator fillColor)
	{
		this.fillColor = fillColor;
		return this;
	}
	
	
	/**
	 * @return the center
	 */
	public IVector2 getCenter()
	{
		return center;
	}
	
	
	/**
	 * @return the lineColor
	 */
	public IColorAnimator getLineColor()
	{
		return lineColor;
	}
	
	
	/**
	 * @return the fillColor
	 */
	public IColorAnimator getFillColor()
	{
		return fillColor;
	}
	
	
	/**
	 * @return the strokeWidth
	 */
	public float getStrokeWidth()
	{
		return strokeWidth;
	}
}
