/*
 * Copyright (c) 2009 - 2017, DHBW Mannheim - Tigers Mannheim
 */
package edu.tigers.sumatra.gamelog;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import com.google.protobuf.AbstractMessage;

import edu.tigers.sumatra.gamelog.SSLGameLogReader.SSLGameLogfileEntry;


/**
 * Logs referee and vision data according to: http://wiki.robocup.org/Small_Size_League/Game_Logs
 * 
 * @author AndreR <andre@ryll.cc>
 */
public class SSLGameLogWriter
{
	private static final Logger	log					= Logger.getLogger(SSLGameLogWriter.class.getName());
	
	private static final String	GAMELOG_PATH		= "data/gamelog";
	private static final String	FILE_TYPE_HEADER	= "SSL_LOG_FILE";
	private static final int		VERSION				= 1;
	
	private DataOutputStream		outputStream;
	
	
	/**
	 * Open a gamelog for writing using current date/time as filename.
	 */
	public void open()
	{
		SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		dt.setTimeZone(TimeZone.getDefault());
		
		open(dt.format(new Date()));
	}
	
	
	/**
	 * Open a gamelog for writing.
	 * Suppress missing finally block and status code return ignorance
	 * 
	 * @param filename
	 */
	@SuppressWarnings({ "squid:S2095", "squid:S899" })
	public void open(final String filename)
	{
		String fullName = GAMELOG_PATH + "/" + filename + ".log.gz";
		
		try
		{
			File folder = new File(GAMELOG_PATH);
			folder.mkdirs();
			
			// open file
			outputStream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(fullName, false), 8192));
			
			// write header
			outputStream.writeBytes(FILE_TYPE_HEADER);
			outputStream.writeInt(VERSION);
		} catch (IOException e)
		{
			log.error("Exception on opening gamelog file", e);
		}
	}
	
	
	/**
	 * Open a gamelog for writing.
	 * Suppress missing finally block and status code return ignorance
	 * 
	 * @param fullName
	 */
	@SuppressWarnings({ "squid:S2095", "squid:S899" })
	public void openPath(final String fullName)
	{
		try
		{
			// open file
			outputStream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(fullName, false), 8192));
			
			// write header
			outputStream.writeBytes(FILE_TYPE_HEADER);
			outputStream.writeInt(VERSION);
		} catch (IOException e)
		{
			log.error("Exception on opening gamelog file", e);
		}
	}
	
	
	/**
	 * @return
	 */
	public boolean isOpen()
	{
		return outputStream != null;
	}
	
	
	/**
	 * Close gamelog.
	 */
	public void close()
	{
		if (outputStream != null)
		{
			try
			{
				outputStream.close();
			} catch (IOException e)
			{
				log.error("Exception on closing gamelog file", e);
			}
			outputStream = null;
		}
	}
	
	
	/**
	 * Write gamelog entry to file.
	 * 
	 * @param entry
	 */
	public void write(final SSLGameLogfileEntry entry)
	{
		if (outputStream == null)
		{
			return;
		}
		
		long time = entry.getTimestamp();
		byte[] data = entry.getProtobufMsg().toByteArray();
		EMessageType type = entry.getVisionPacket().isPresent() ? EMessageType.SSL_VISION_2014
				: EMessageType.SSL_REFBOX_2013;
		
		try
		{
			outputStream.writeLong(time);
			outputStream.writeInt(type.getId());
			outputStream.writeInt(data.length);
			outputStream.write(data);
		} catch (IOException e)
		{
			log.error("Exception writing to gamelog", e);
		}
	}
	
	
	/**
	 * Write a message to the output stream.
	 * 
	 * @param msg
	 * @param type
	 */
	public void write(final AbstractMessage msg, final EMessageType type)
	{
		if (outputStream == null)
		{
			return;
		}
		
		long time = System.nanoTime();
		byte[] data = msg.toByteArray();
		
		try
		{
			outputStream.writeLong(time);
			outputStream.writeInt(type.getId());
			outputStream.writeInt(data.length);
			outputStream.write(data);
		} catch (IOException e)
		{
			log.error("Exception writing to gamelog", e);
		}
	}
}
