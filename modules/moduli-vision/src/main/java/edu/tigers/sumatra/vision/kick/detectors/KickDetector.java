/*
 * Copyright (c) 2009 - 2017, DHBW Mannheim - TIGERs Mannheim
 */
package edu.tigers.sumatra.vision.kick.detectors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.Validate;
import org.apache.commons.math3.util.Pair;
import org.apache.log4j.Logger;

import com.github.g3force.configurable.ConfigRegistration;
import com.github.g3force.configurable.Configurable;

import edu.tigers.sumatra.drawable.DrawableAnnotation;
import edu.tigers.sumatra.drawable.IDrawableShape;
import edu.tigers.sumatra.ids.BotID;
import edu.tigers.sumatra.math.vector.IVector2;
import edu.tigers.sumatra.math.vector.Vector2;
import edu.tigers.sumatra.math.vector.Vector2f;
import edu.tigers.sumatra.vision.data.FilteredVisionBot;
import edu.tigers.sumatra.vision.data.FrameRecord;
import edu.tigers.sumatra.vision.data.KickEvent;
import edu.tigers.sumatra.vision.kick.validators.DirectionValidator;
import edu.tigers.sumatra.vision.kick.validators.DistanceValidator;
import edu.tigers.sumatra.vision.kick.validators.IKickValidator;
import edu.tigers.sumatra.vision.kick.validators.InFrontValidator;
import edu.tigers.sumatra.vision.kick.validators.IncreasingDistanceValidator;
import edu.tigers.sumatra.vision.kick.validators.VelocityValidator;
import edu.tigers.sumatra.vision.tracker.BallTracker.MergedBall;


/**
 * Detect kicks based on velocity and direction changes.
 * 
 * @author AndreR <andre@ryll.cc>
 */
public class KickDetector implements IKickDetector
{
	@SuppressWarnings("unused")
	private static final Logger		log							= Logger
			.getLogger(KickDetector.class.getName());
	
	private LinkedList<FrameRecord>	frameHistory				= new LinkedList<>();
	
	private static int					frameHistorySize			= 5;
	
	private long							lastKickTimestamp;
	
	private List<IKickValidator>		kickValidators				= new ArrayList<>();
	private String							lastKVText					= "";
	private IVector2 lastKnownBallPosition = Vector2f.ZERO_VECTOR;
	
	@Configurable(defValue = "0.1", comment = "Minimum time between two kicks [s]")
	private static double				minDeltaTime				= 0.1;
	
	static
	{
		ConfigRegistration.registerClass("vision", KickDetector.class);
	}
	
	
	/**
	 * Create kick detector.
	 */
	public KickDetector()
	{
		kickValidators.add(new DistanceValidator());
		kickValidators.add(new VelocityValidator());
		kickValidators.add(new InFrontValidator());
		kickValidators.add(new IncreasingDistanceValidator());
	}
	
	
	/**
	 * Add new flat ball record to the kick detector.
	 * 
	 * @param mergedBall
	 * @param mergedRobots
	 */
	@Override
	public KickEvent addRecord(final MergedBall mergedBall, final List<FilteredVisionBot> mergedRobots)
	{
		lastKnownBallPosition = mergedBall.getCamPos();
		
		FrameRecord rec = new FrameRecord(mergedBall, mergedRobots);
		frameHistory.add(rec);
		
		if (frameHistory.size() > frameHistorySize)
		{
			frameHistory.remove();
		}
		
		if (frameHistory.size() == frameHistorySize)
		{
			return processFrames();
		}
		
		return null;
	}
	
	
	/**
	 * Clear history and kick timestamp.
	 */
	@Override
	public void reset()
	{
		lastKickTimestamp = 0;
		frameHistory.clear();
	}
	
	
	private KickEvent processFrames()
	{
		List<MergedBall> balls = frameHistory.stream()
				.map(FrameRecord::getBall)
				.collect(Collectors.toList());
		
		Map<BotID, List<FilteredVisionBot>> bots = frameHistory.stream()
				.flatMap(f -> f.getRobots().stream())
				.collect(Collectors.groupingBy(FilteredVisionBot::getBotID));
		
		// remove bots from checklist if no full history is available (new bot)
		bots.entrySet().removeIf(e -> e.getValue().size() < frameHistorySize);
		
		// remove bots from checklist which are too far away
		bots.entrySet().removeIf(e -> e.getValue().get(0).getPos().distanceTo(balls.get(0).getCamPos()) > 1000);
		
		lastKVText = "";
		
		StringBuilder kvText = new StringBuilder();
		
		List<FilteredVisionBot> kickedBot = null;
		for (List<FilteredVisionBot> b : bots.values())
		{
			Validate.isTrue(b.size() == balls.size());
			
			boolean kick = true;
			kvText.append("KV (" + b.get(0).getBotID() + "):");
			for (IKickValidator k : kickValidators)
			{
				boolean valid = k.validateKick(b, balls);
				kvText.append(valid ? "+" : "-");
				kvText.append(k.getName() + ",");
				
				if (!valid)
				{
					kick = false;
				}
			}
			
			kvText.append("\n");
			
			if (kick)
			{
				kickedBot = b;
				break;
			}
		}
		
		lastKVText = kvText.toString();
		
		if ((kickedBot != null) && (Math.abs((balls.get(0).getTimestamp() - lastKickTimestamp) * 1e-9) > minDeltaTime))
		{
			FilteredVisionBot bot = kickedBot.get(0);
			log.debug("Kick detected, Bot: " + bot.getBotID());
			
			KickEvent kick = new KickEvent(balls.get(0).getCamPos(), bot, balls.get(0).getTimestamp(), balls, false);
			lastKickTimestamp = balls.get(0).getTimestamp();
			
			Optional<Pair<Long, IVector2>> backtrack = DirectionValidator.backtrack(kickedBot, balls);
			if (backtrack.isPresent())
			{
				log.debug("Backtrack possible");
				kick = new KickEvent(backtrack.get().getSecond(), bot, backtrack.get().getFirst(), balls, false);
				lastKickTimestamp = backtrack.get().getFirst();
			}
			
			return kick;
		}
		
		return null;
	}
	
	
	@Override
	public List<IDrawableShape> getDrawableShapes()
	{
		List<IDrawableShape> shapes = new ArrayList<>();
		
		DrawableAnnotation kvText = new DrawableAnnotation(lastKnownBallPosition, lastKVText);
		kvText.setOffset(Vector2.fromXY(0, 30));
		kvText.setCenterHorizontally(true);
		kvText.setFontHeight(20);
		kvText.setColor(Color.MAGENTA);
		shapes.add(kvText);
		
		return shapes;
	}
}
