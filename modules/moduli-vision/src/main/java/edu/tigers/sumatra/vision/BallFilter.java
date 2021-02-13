/*
 * Copyright (c) 2009 - 2020, DHBW Mannheim - TIGERs Mannheim
 */
package edu.tigers.sumatra.vision;

import com.github.g3force.configurable.ConfigRegistration;
import com.github.g3force.configurable.Configurable;
import edu.tigers.sumatra.drawable.DrawableAnnotation;
import edu.tigers.sumatra.drawable.DrawableCircle;
import edu.tigers.sumatra.drawable.IDrawableShape;
import edu.tigers.sumatra.geometry.Geometry;
import edu.tigers.sumatra.math.vector.IVector2;
import edu.tigers.sumatra.math.vector.IVector3;
import edu.tigers.sumatra.math.vector.Vector2;
import edu.tigers.sumatra.math.vector.Vector3;
import edu.tigers.sumatra.math.vector.Vector3f;
import edu.tigers.sumatra.vision.BallFilterPreprocessor.BallFilterPreprocessorOutput;
import edu.tigers.sumatra.vision.data.BallTrajectoryState;
import edu.tigers.sumatra.vision.data.EBallState;
import edu.tigers.sumatra.vision.data.FilteredVisionBall;
import edu.tigers.sumatra.vision.tracker.BallTracker.MergedBall;
import lombok.Value;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * @author AndreR
 */
public class BallFilter
{
	private EBallState ballState = EBallState.ROLLING;
	private IVector2 ballPosHint;
	private IVector3 lastKnownPosition = Vector3f.ZERO_VECTOR;
	private CircularFifoQueue<MergedBall> mergedBallHistory = new CircularFifoQueue<>(50);

	@Configurable(defValue = "false", comment = "Always use merged ball velocity instead of kick model velocity.")
	private static boolean alwaysUseMergedBallVelocity = false;

	static
	{
		ConfigRegistration.registerClass("vision", BallFilter.class);
	}


	/**
	 * Insert ball position hint.
	 *
	 * @param pos
	 */
	public void resetBall(final IVector2 pos)
	{
		ballPosHint = pos;
	}


	/**
	 * Update the ball filter with output from the preprocessor.
	 *
	 * @param preInput
	 * @param lastFilteredBall
	 * @param timestamp
	 * @return
	 */
	public BallFilterOutput update(final BallFilterPreprocessorOutput preInput,
			final FilteredVisionBall lastFilteredBall, final long timestamp)
	{
		// process ball position hint if there is one
		if (ballPosHint != null)
		{
			FilteredVisionBall ball = FilteredVisionBall.builder()
					.withTimestamp(timestamp)
					.withBallTrajectoryState(edu.tigers.sumatra.vision.data.BallTrajectoryState.builder()
							.withPos(Vector3.from2d(ballPosHint, 0))
							.withVel(Vector3f.ZERO_VECTOR)
							.withAcc(Vector3f.ZERO_VECTOR)
							.withChipped(false)
							.withVSwitchToRoll(0)
							.build())
					.withLastVisibleTimestamp(timestamp)
					.build();

			ballPosHint = null;

			return new BallFilterOutput(ball, ball.getPos(), preInput);
		}

		// returned last output if there is no valid ball at all
		if (preInput.getMergedBall().isEmpty())
		{
			FilteredVisionBall ball = FilteredVisionBall.builder()
					.withTimestamp(timestamp)
					.withBallTrajectoryState(edu.tigers.sumatra.vision.data.BallTrajectoryState.builder()
							.withPos(lastFilteredBall.getPos())
							.withVel(Vector3f.ZERO_VECTOR)
							.withAcc(Vector3f.ZERO_VECTOR)
							.withChipped(false)
							.withVSwitchToRoll(0)
							.build())
					.withLastVisibleTimestamp(lastFilteredBall.getLastVisibleTimestamp())
					.build();

			return new BallFilterOutput(ball, lastFilteredBall.getPos(), preInput);
		}

		MergedBall mergedBall = preInput.getMergedBall().get();
		long lastVisibleTimestamp = lastFilteredBall.getLastVisibleTimestamp();

		if (mergedBall.getLatestCamBall().isPresent())
		{
			lastKnownPosition = mergedBall.getLatestCamBall().get().getPos();
			lastVisibleTimestamp = mergedBall.getLatestCamBall().get().gettCapture();
			mergedBallHistory.add(mergedBall);
		}

		Optional<BallTrajectoryState> optKickFitState = preInput.getKickFitState();

		IVector3 pos;
		IVector3 vel;
		IVector3 acc;
		double vSwitch;

		if (optKickFitState.isPresent())
		{
			BallTrajectoryState kickFitState = optKickFitState.get();
			acc = kickFitState.getAcc();
			vel = kickFitState.getVel();
			vSwitch = kickFitState.getVSwitchToRoll();

			if (kickFitState.isChipped())
			{
				ballState = EBallState.AIRBORNE;
				pos = kickFitState.getPos();
				lastKnownPosition = pos;
			} else
			{
				ballState = EBallState.KICKED;
				pos = Vector3.from2d(mergedBall.getCamPos(), 0);
			}

			if (alwaysUseMergedBallVelocity)
			{
				vel = Vector3.from2d(vel.getXYVector().scaleToNew(mergedBall.getFiltVel().getLength2()), vel.z());
			}
		} else
		{
			ballState = EBallState.ROLLING;
			pos = Vector3.from2d(mergedBall.getCamPos(), 0);
			vel = Vector3.from2d(mergedBall.getFiltVel(), 0);
			acc = Vector3.from2d(mergedBall.getFiltVel().scaleToNew(-Geometry.getBallParameters().getAccRoll()), 0);
			vSwitch = vel.getLength2();
		}

		FilteredVisionBall filteredBall = FilteredVisionBall.builder()
				.withTimestamp(timestamp)
				.withBallTrajectoryState(edu.tigers.sumatra.vision.data.BallTrajectoryState.builder()
						.withPos(pos)
						.withVel(vel)
						.withAcc(acc)
						.withChipped(ballState == EBallState.AIRBORNE)
						.withVSwitchToRoll(vSwitch)
						.build())
				.withLastVisibleTimestamp(lastVisibleTimestamp)
				.build();

		return new BallFilterOutput(filteredBall, lastKnownPosition, preInput);
	}


	public List<IDrawableShape> getShapes()
	{
		List<IDrawableShape> shapes = new ArrayList<>();

		for (MergedBall b : mergedBallHistory)
		{
			DrawableCircle ballPos = new DrawableCircle(b.getCamPos(), 10, Color.GREEN);
			ballPos.setStrokeWidth(3);
			shapes.add(ballPos);
		}

		DrawableAnnotation state = new DrawableAnnotation(lastKnownPosition.getXYVector(), ballState.toString());
		state.withOffset(Vector2.fromXY(0, -100));
		state.withCenterHorizontally(true);
		state.setStrokeWidth(30);
		shapes.add(state);

		return shapes;
	}


	/**
	 * Output of final ball filter.
	 */
	@Value
	public static class BallFilterOutput
	{
		FilteredVisionBall filteredBall;
		IVector3 lastKnownPosition;
		BallFilterPreprocessorOutput preprocessorOutput;
	}
}
