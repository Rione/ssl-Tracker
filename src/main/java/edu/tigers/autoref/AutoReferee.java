/*
 * Copyright (c) 2009 - 2025, DHBW Mannheim - TIGERs Mannheim
 */
package edu.tigers.autoref;

import edu.tigers.base.BaseApp;
import edu.tigers.sumatra.model.SumatraModel;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;


/**
 * Main class for Tracker output only.
 */
@Log4j2
public final class AutoReferee extends BaseApp implements Runnable
{
	@Setter(onMethod_ = @CommandLine.Option(
			names = { "-va", "--visionAddress" },
			defaultValue = "${env:AUTOREF_VISION_ADDRESS}",
			description = "address:port for vision")
	)
	private String visionAddress;

	@Setter(onMethod_ = @CommandLine.Option(
			names = { "-ta", "--trackerAddress" },
			defaultValue = "${env:AUTOREF_TRACKER_ADDRESS}",
			description = "address:port for tracker")
	)
	private String trackerAddress;


	public static void main(final String[] args)
	{
		new CommandLine(new AutoReferee()).execute(args);
	}


	@Override
	public void run()
	{
		log.info("Starting Tracker Output {}", SumatraModel.getVersion());

		ifNotNull(visionAddress, this::updateVisionAddress);
		ifNotNull(trackerAddress, this::updateTrackerAddress);

		loadModules();
		start();

		log.trace("Started Tracker Output");
	}


	@Override
	protected void loadModules()
	{
		SumatraModel.getInstance().setCurrentModuliConfig("tracker-only.xml");
		super.loadModules();
	}
}
