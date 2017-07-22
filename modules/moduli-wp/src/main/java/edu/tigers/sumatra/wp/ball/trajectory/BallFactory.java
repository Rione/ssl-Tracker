/*
 * Copyright (c) 2009 - 2017, DHBW Mannheim - TIGERs Mannheim
 */

package edu.tigers.sumatra.wp.ball.trajectory;

import com.github.g3force.configurable.ConfigRegistration;
import com.github.g3force.configurable.Configurable;

import edu.tigers.sumatra.math.vector.IVector2;
import edu.tigers.sumatra.math.vector.IVector3;
import edu.tigers.sumatra.model.SumatraModel;
import edu.tigers.sumatra.wp.ball.prediction.IChipBallConsultant;
import edu.tigers.sumatra.wp.ball.prediction.IStraightBallConsultant;
import edu.tigers.sumatra.wp.ball.trajectory.chipped.FixedLossPlusRollingBallTrajectory;
import edu.tigers.sumatra.wp.ball.trajectory.chipped.FixedLossPlusRollingBallTrajectory.FixedLossPlusRollingParameters;
import edu.tigers.sumatra.wp.ball.trajectory.chipped.FixedLossPlusRollingConsultant;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseDynamicVelBallTrajectory;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseDynamicVelBallTrajectory.TwoPhaseDynamicVelParameters;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseDynamicVelConsultant;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseFixedVelBallTrajectory;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseFixedVelBallTrajectory.TwoPhaseFixedVelParameters;
import edu.tigers.sumatra.wp.ball.trajectory.flat.TwoPhaseFixedVelConsultant;
import edu.tigers.sumatra.wp.data.BallTrajectoryState;


/**
 * Factory class for creating classes for the configured ball models.
 *
 * @author Nicolai Ommer <nicolai.ommer@gmail.com>
 * @author AndreR <andre@ryll.cc>
 */
public final class BallFactory
{
	@Configurable(comment = "Type of model that will be created for flat balls", spezis = { "",
			"SUMATRA" }, defValueSpezis = { "TWO_PHASE_DYNAMIC_VEL", "TWO_PHASE_DYNAMIC_VEL", })
	private static EFlatBallModel	ballModelTypeFlat	= EFlatBallModel.TWO_PHASE_DYNAMIC_VEL;
	
	@Configurable(comment = "Type of model that will be created for chipped balls", defValue = "FIXED_LOSS_PLUS_ROLLING")
	private static EChipBallModel	ballModelTypeChip	= EChipBallModel.FIXED_LOSS_PLUS_ROLLING;
	
	static
	{
		ConfigRegistration.registerClass("wp", BallFactory.class);
	}
	
	
	@SuppressWarnings("unused")
	private BallFactory()
	{
	}
	
	
	/**
	 * Update configs
	 */
	public static void updateConfigs()
	{
		ConfigRegistration.applySpezi("wp", SumatraModel.getInstance().getEnvironment());
	}
	
	
	/**
	 * Create a ball trajectory with the default configured implementation
	 * 
	 * @param state the ball state on which the trajectory is based
	 * @return a new ball trajectory
	 */
	public static ABallTrajectory createTrajectory(final BallTrajectoryState state)
	{
		final ABallTrajectory trajectory;
		
		if (state.isChipped())
		{
			switch (ballModelTypeChip)
			{
				case FIXED_LOSS_PLUS_ROLLING:
					trajectory = FixedLossPlusRollingBallTrajectory.fromState(state.getPos(), state.getVel(),
							new FixedLossPlusRollingParameters());
					break;
				default:
					throw new UnsupportedOperationException();
			}
		} else
		{
			switch (ballModelTypeFlat)
			{
				case TWO_PHASE_FIXED_VEL:
					trajectory = TwoPhaseFixedVelBallTrajectory.fromState(state.getPos(), state.getVel(),
							new TwoPhaseFixedVelParameters());
					break;
				case TWO_PHASE_DYNAMIC_VEL:
					trajectory = TwoPhaseDynamicVelBallTrajectory.fromState(state.getPos(), state.getVel(),
							state.getvSwitchToRoll(),
							new TwoPhaseDynamicVelParameters());
					break;
				default:
					throw new UnsupportedOperationException();
			}
		}
		
		return trajectory;
	}
	
	
	/**
	 * Create a ball trajectory based on a kick
	 * 
	 * @param kickPos [mm]
	 * @param kickVel [mm/s]
	 * @param chip
	 * @return
	 */
	public static ABallTrajectory createTrajectoryFromKick(final IVector2 kickPos, final IVector3 kickVel,
			final boolean chip)
	{
		final ABallTrajectory trajectory;
		
		if (chip)
		{
			switch (ballModelTypeChip)
			{
				case FIXED_LOSS_PLUS_ROLLING:
					trajectory = FixedLossPlusRollingBallTrajectory.fromKick(kickPos, kickVel,
							new FixedLossPlusRollingParameters());
					break;
				default:
					throw new UnsupportedOperationException();
			}
		} else
		{
			switch (ballModelTypeFlat)
			{
				case TWO_PHASE_FIXED_VEL:
					trajectory = TwoPhaseFixedVelBallTrajectory.fromKick(kickPos, kickVel, new TwoPhaseFixedVelParameters());
					break;
				case TWO_PHASE_DYNAMIC_VEL:
					trajectory = TwoPhaseDynamicVelBallTrajectory.fromKick(kickPos, kickVel,
							new TwoPhaseDynamicVelParameters());
					break;
				default:
					throw new UnsupportedOperationException();
			}
		}
		
		return trajectory;
	}
	
	
	/**
	 * Create a consultant for straight kicks with the default configured implementation
	 * 
	 * @return a new ball consultant for straight kicks
	 */
	public static IStraightBallConsultant createStraightConsultant()
	{
		switch (ballModelTypeFlat)
		{
			case TWO_PHASE_FIXED_VEL:
				return new TwoPhaseFixedVelConsultant(new TwoPhaseFixedVelParameters());
			case TWO_PHASE_DYNAMIC_VEL:
				return new TwoPhaseDynamicVelConsultant(new TwoPhaseDynamicVelParameters());
			default:
				throw new UnsupportedOperationException();
		}
	}
	
	
	/**
	 * Create a consultant for chip kicks with the default configured implementation
	 *
	 * @return a new ball consultant for chip kicks
	 */
	public static IChipBallConsultant createChipConsultant()
	{
		switch (ballModelTypeChip)
		{
			case FIXED_LOSS_PLUS_ROLLING:
				return new FixedLossPlusRollingConsultant(new FixedLossPlusRollingParameters());
			default:
				throw new UnsupportedOperationException();
		}
	}
}
