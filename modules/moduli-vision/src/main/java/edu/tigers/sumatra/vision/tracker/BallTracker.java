/*
 * *********************************************************
 * Copyright (c) 2009 - 2016, DHBW Mannheim - Tigers Mannheim
 * Project: TIGERS - Sumatra
 * Date: 24.11.2016
 * Author(s): AndreR <andre@ryll.cc>
 * *********************************************************
 */
package edu.tigers.sumatra.vision.tracker;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.Validate;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;

import com.github.g3force.configurable.ConfigRegistration;
import com.github.g3force.configurable.Configurable;

import edu.tigers.sumatra.cam.data.CamBall;
import edu.tigers.sumatra.filter.tracking.TrackingFilterPosVel2D;
import edu.tigers.sumatra.math.rectangle.IRectangle;
import edu.tigers.sumatra.math.vector.AVector2;
import edu.tigers.sumatra.math.vector.IVector2;
import edu.tigers.sumatra.vision.data.CamBallInternal;
import edu.tigers.sumatra.vision.data.FilteredVisionBall;
import edu.tigers.sumatra.vision.data.RobotCollisionShape;
import edu.tigers.sumatra.vision.data.RobotCollisionShape.CollisionResult;


/**
 * Tracks and filters a single ball.
 * 
 * @author AndreR <andre@ryll.cc>
 */
public class BallTracker
{
	@SuppressWarnings("unused")
	private static final Logger				log									= Logger
			.getLogger(BallTracker.class.getName());
	
	private final TrackingFilterPosVel2D	filter;
	
	
	private long									lastPredictTimestamp;
	private long									lastInFieldTimestamp;
	private double									lastDtDeviation					= 0.0;
	
	private int										health								= 2;
	
	private CamBall								lastCamBall;
	private boolean								updated								= false;
	
	private double									maxDistance							= -1.0;
	
	
	@Configurable(defValue = "100.0")
	private static double						initialCovarianceXY				= 100.0;
	@Configurable(defValue = "0.1")
	private static double						modelError							= 0.1;
	@Configurable(defValue = "2.0")
	private static double						measError							= 2.0;
	@Configurable(defValue = "10000.0", comment = "Maximum assumed ball speed in [mm/s] to filter outliers")
	private static double						maxLinearVel						= 10000.0;
	@Configurable(defValue = "1000.0", comment = "Increase measurement error depending on frame time deviation from average.")
	private static double						measErrorDtDeviationPenalty	= 1000.0;
	@Configurable(defValue = "1.5", comment = "Factor to weight stdDeviation during tracker merging, reasonable range: 1.0 - 2.0. High values lead to more jitter")
	private static double						mergePower							= 1.5;
	@Configurable(defValue = "20", comment = "Reciprocal health is used as uncertainty, increased on update, decreased on prediction")
	private static int							maxHealth							= 20;
	
	static
	{
		ConfigRegistration.registerClass("vision", BallTracker.class);
	}
	
	
	/**
	 * Create a new ball tracker.
	 * 
	 * @param ball
	 */
	public BallTracker(final CamBall ball)
	{
		filter = new TrackingFilterPosVel2D(ball.getPos().getXYVector(), initialCovarianceXY, modelError, measError,
				ball.gettCapture());
		
		lastPredictTimestamp = ball.gettCapture();
		lastInFieldTimestamp = ball.gettCapture();
		lastCamBall = ball;
	}
	
	
	/**
	 * Create a new ball tracker.
	 * 
	 * @param camBall
	 * @param filtBall
	 */
	public BallTracker(final CamBall camBall, final FilteredVisionBall filtBall)
	{
		RealVector initState = camBall.getPos().getXYVector().toRealVector()
				.append(filtBall.getVel().getXYVector().toRealVector());
		filter = new TrackingFilterPosVel2D(initState, initialCovarianceXY, modelError, measError,
				camBall.gettCapture());
		
		lastPredictTimestamp = camBall.gettCapture();
		lastInFieldTimestamp = camBall.gettCapture();
		lastCamBall = camBall;
	}
	
	
	/**
	 * Do a prediction step on to a specific time.
	 * 
	 * @param timestamp time in [ns]
	 * @param avgFrameDt average frame delta time in [s]
	 * @param bots
	 */
	public void predict(final long timestamp, final double avgFrameDt, final List<RobotCollisionShape> bots)
	{
		double dtInSec = (timestamp - lastPredictTimestamp) * 1e-9;
		
		if (Math.abs(lastDtDeviation) > 0.002)
		{
			lastDtDeviation = 0;
		} else
		{
			lastDtDeviation = avgFrameDt - dtInSec;
		}
		
		processCollisions(bots);
		
		filter.setMeasurementError(measError + (Math.abs(lastDtDeviation) * measErrorDtDeviationPenalty));
		
		filter.predict(timestamp);
		
		lastPredictTimestamp = timestamp;
		
		if (health > 1)
		{
			health--;
		}
	}
	
	
	/**
	 * Update this tracker with a camera measurement.
	 * 
	 * @param ball
	 * @param fieldSize field size, can be unknown
	 * @return True if the measurement has been accepted by the tracker (no outlier)
	 */
	public boolean update(final CamBall ball, final Optional<IRectangle> fieldSize)
	{
		IVector2 ballPos2D = ball.getPos().getXYVector();
		
		long tCapture = ball.gettCapture();
		
		// calculate delta time since last update
		double dtInSec = (tCapture - lastCamBall.gettCapture()) * 1e-9;
		
		// calculate distance of this ball to our internal prediction
		double distanceToPrediction = filter.getPositionEstimate().distanceTo(ballPos2D);
		
		// ignore the ball if it is too far away from our prediction...
		// ... we have a hard limit of maxDistance
		if ((maxDistance > 0) && (distanceToPrediction > maxDistance))
		{
			return false;
		}
		// ... and a variable distance based on the assumed max ball speed
		if (distanceToPrediction > (dtInSec * maxLinearVel))
		{
			// measurement too far away => refuse update
			return false;
		}
		
		// we have an update, increase health/certainty in this tracker
		if (health < maxHealth)
		{
			health += 2;
		}
		
		// run correction on tracking filter
		filter.correct(ballPos2D);
		
		// if we know the field size, check if the ball is inside it
		if (fieldSize.isPresent())
		{
			if (fieldSize.get().isPointInShape(ballPos2D))
			{
				lastInFieldTimestamp = tCapture;
			}
		} else
		{
			lastInFieldTimestamp = tCapture;
		}
		
		// store cam ball for next run
		lastCamBall = ball;
		updated = true;
		
		return true;
	}
	
	
	private void processCollisions(final List<RobotCollisionShape> bots)
	{
		for (RobotCollisionShape col : bots)
		{
			CollisionResult result = col.getCollision(filter.getPositionEstimate(), filter.getVelocityEstimate());
			switch (result.getLocation())
			{
				case CIRCLE:
					filter.resetCovariance(initialCovarianceXY);
					break;
				case FRONT:
					filter.setVelocity(AVector2.ZERO_VECTOR);
					filter.resetCovariance(initialCovarianceXY);
					break;
				case NONE:
				default:
					break;
			}
		}
	}
	
	
	public double getUncertainty()
	{
		return 1.0 / health;
	}
	
	
	/**
	 * @return timestamp in [ns]
	 */
	public long getLastUpdateTimestamp()
	{
		return lastCamBall.gettCapture();
	}
	
	
	public int getCameraId()
	{
		return lastCamBall.getCameraId();
	}
	
	
	/**
	 * Get position estimate at specific timestamp.
	 * 
	 * @param timestamp Query time.
	 * @return Position in [mm]
	 */
	public IVector2 getPosition(final long timestamp)
	{
		return filter.getPositionEstimate(timestamp);
	}
	
	
	/**
	 * Get linear velocity estimate.
	 * 
	 * @return Velocity in [mm/s]
	 */
	public IVector2 getVelocity()
	{
		return filter.getVelocityEstimate();
	}
	
	
	/**
	 * @return the filter
	 */
	public TrackingFilterPosVel2D getFilter()
	{
		return filter;
	}
	
	
	/**
	 * This function merges a variable number of ball trackers and makes a filtered vision ball out of them.
	 * Trackers are weighted according to their state uncertainties. A tracker with high uncertainty
	 * has less influence on the final merge result.
	 * 
	 * @param balls List of ball trackers. Must not be empty.
	 * @param timestamp Extrapolation time stamp to use for the final ball.
	 * @return Merged filtered vision ball.
	 */
	public static MergedBall mergeBallTrackers(final List<BallTracker> balls, final long timestamp)
	{
		Validate.notEmpty(balls);
		
		double totalPosUnc = 0;
		double totalVelUnc = 0;
		
		CamBall lastCamBall = null;
		double dtDev = 0;
		
		// calculate sum of all uncertainties
		for (BallTracker t : balls)
		{
			double f = t.getUncertainty();
			totalPosUnc += Math.pow(t.filter.getPositionUncertainty().getLength() * f, -mergePower);
			totalVelUnc += Math.pow(t.filter.getVelocityUncertainty().getLength() * f, -mergePower);
			
			if (t.getUpdatedAndReset())
			{
				lastCamBall = t.getLastCamBall();
				dtDev = t.getLastDtDeviation();
			}
		}
		
		// all uncertainties must be > 0, otherwise we found a bug
		Validate.isTrue(totalPosUnc > 0);
		Validate.isTrue(totalVelUnc > 0);
		
		IVector2 pos = AVector2.ZERO_VECTOR;
		IVector2 posCam = AVector2.ZERO_VECTOR;
		IVector2 vel = AVector2.ZERO_VECTOR;
		
		// take all trackers and calculate their pos/vel sum weighted by uncertainty.
		// Trackers with high uncertainty have less influence on the merged result.
		for (BallTracker t : balls)
		{
			double f = t.getUncertainty();
			pos = pos.addNew(t.filter.getPositionEstimate(timestamp)
					.multiplyNew(Math.pow(t.filter.getPositionUncertainty().getLength() * f, -mergePower)));
			posCam = posCam.addNew(t.getLastCamBall().getPos().getXYVector()
					.multiplyNew(Math.pow(t.filter.getPositionUncertainty().getLength() * f, -mergePower)));
			vel = vel.addNew(t.filter.getVelocityEstimate()
					.multiplyNew(Math.pow(t.filter.getVelocityUncertainty().getLength() * f, -mergePower)));
		}
		
		pos = pos.multiplyNew(1.0 / totalPosUnc);
		posCam = posCam.multiplyNew(1.0 / totalPosUnc);
		vel = vel.multiplyNew(1.0 / totalVelUnc);
		
		return new MergedBall(pos, posCam, vel, timestamp, lastCamBall, dtDev);
	}
	
	/**
	 * Merge result of multiple ball trackers.
	 * 
	 * @author AndreR
	 */
	public static class MergedBall
	{
		private final IVector2			filtPos;
		private final IVector2			camPos;
		private final IVector2			filtVel;
		private final long				timestamp;
		private final CamBallInternal	latestCamBall;
		
		
		/**
		 * @param filtPos
		 * @param camPos
		 * @param filtVel
		 * @param timestamp
		 * @param latestCamBall
		 * @param dtDeviation
		 */
		public MergedBall(final IVector2 filtPos, final IVector2 camPos, final IVector2 filtVel, final long timestamp,
				final CamBall latestCamBall,
				final double dtDeviation)
		{
			this.filtPos = filtPos;
			this.camPos = camPos;
			this.filtVel = filtVel;
			this.timestamp = timestamp;
			if (latestCamBall == null)
			{
				this.latestCamBall = null;
			} else
			{
				this.latestCamBall = new CamBallInternal(latestCamBall, dtDeviation);
			}
		}
		
		
		/**
		 * @return the filtPos
		 */
		public IVector2 getFiltPos()
		{
			return filtPos;
		}
		
		
		/**
		 * @return the camPos
		 */
		public IVector2 getCamPos()
		{
			return camPos;
		}
		
		
		/**
		 * @return the filtVel
		 */
		public IVector2 getFiltVel()
		{
			return filtVel;
		}
		
		
		/**
		 * @return the timestamp
		 */
		public long getTimestamp()
		{
			return timestamp;
		}
		
		
		/**
		 * If this optional is empty the merged ball solely depends on predicted data.
		 * Otherwise, the most recent raw CamBall is present.
		 * 
		 * @return the latestCamBall
		 */
		public Optional<CamBallInternal> getLatestCamBall()
		{
			return Optional.ofNullable(latestCamBall);
		}
	}
	
	
	/**
	 * @return the maxLinearVel
	 */
	public static double getMaxLinearVel()
	{
		return maxLinearVel;
	}
	
	
	/**
	 * @return the maxDistance
	 */
	public double getMaxDistance()
	{
		return maxDistance;
	}
	
	
	/**
	 * @param maxDistance the maxDistance to set
	 */
	public void setMaxDistance(final double maxDistance)
	{
		this.maxDistance = maxDistance;
	}
	
	
	/**
	 * @return the lastInFieldTimestamp
	 */
	public long getLastInFieldTimestamp()
	{
		return lastInFieldTimestamp;
	}
	
	
	/**
	 * @return the lastCamBall
	 */
	public CamBall getLastCamBall()
	{
		return lastCamBall;
	}
	
	
	public boolean getUpdatedAndReset()
	{
		boolean ret = updated;
		updated = false;
		return ret;
	}
	
	
	public double getLastDtDeviation()
	{
		return lastDtDeviation;
	}
}
