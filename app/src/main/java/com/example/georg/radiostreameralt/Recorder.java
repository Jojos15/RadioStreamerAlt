package com.example.georg.radiostreameralt;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

import static com.example.georg.radiostreameralt.Recorder.RECORDING;
import static com.example.georg.radiostreameralt.Recorder.STOPPED;

public class Recorder extends Service
{
	public final static int RECORDING = 1;
	public final static int STOPPED = 2;
	
	@SuppressLint("UseSparseArrays")
	private HashMap<Integer, Recording> rec = new HashMap<>();
	private static Integer key = 0;
	public static int activeRecordings = 0;
	
	@Override
	public void onCreate()
	{
		Log.w("Recorder", "service created");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		String action = intent.getStringExtra("Action");
		switch (action)
		{
			case "RECORD":
			{
				String urlString = intent.getStringExtra("urlString");
				
				rec.put(key, new Recording(date(), urlString, intent.getLongExtra("duration", -1)));
				rec.get(key).start();
				broadcastRecording("RECORDING_ADDED", key); //send main the key for hash address
				Log.w("Recorder", "REC " + key + " START");
				key++;
				activeRecordings++;
				Log.w("activeRecordings", String.valueOf(activeRecordings));
				break;
			}
			case "STOP":
			{
				int passedKey = intent.getIntExtra("key", -1);
				rec.get(passedKey).stop();
				broadcastRecording("RECORDING_STOPPED", passedKey);
				Log.w("Recorder", "REC " + passedKey + " END");
				while (rec.get(passedKey).getStatus() != 2);
				Log.w("activeRecordings", String.valueOf(activeRecordings));
				rec.remove(passedKey);
				if (activeRecordings == 0)
				{
					Log.w("Recorder", "service destroyed");
					stopSelf();
				}
				break;
			}
		}
		return START_STICKY;
	}
	
	private String date()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
		{
			Calendar calendar = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd-kkmmss");
			return sdf.format(calendar.getTime());
		}
		else
		{
			Date d = new Date();
			CharSequence s = DateFormat.format("yyMMdd-kkmmss", d.getTime());
			return (String)s;
		}
	}
	
	@Nullable
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
	
	//function to broadcast hash key of current Recording
	public void broadcastRecording(String action, int key)
	{
		Intent intent = new Intent();
		intent.setAction(action);
		intent.putExtra("key", key);
		sendBroadcast(intent);
	}
}

class Recording
{
	private String date;
	private String urlString;
	private long duration;
	private boolean stopped;
	private int status;
	
	Recording(String date, String urlString, long duration)
	{
		this.date = date;
		this.urlString = urlString;
		this.duration = duration;
		stopped=false;
	}
	
	void start()
	{
		status = RECORDING;
		new Thread(new Runnable(){
			@Override
			public void run() {
				try
				{
					status = 0; //Loading
					long startTimeInSeconds = System.currentTimeMillis()/1000;
					
					URL url = new URL(urlString);
					InputStream inputStream = url.openStream();
					FileOutputStream fileOutputStream;
					File streamsDir = new File(Environment.getExternalStorageDirectory() + "/Streams");
					if (!streamsDir.exists())
					{
						boolean directoryCreated = streamsDir.mkdirs();
						Log.w("Recorder", "Created directory");
						if (!directoryCreated)
						{
							Log.w("Recorder", "Failed to create directory");
						}
					}
					File outputSource = new File(streamsDir,date + ".mp3");
					fileOutputStream = new FileOutputStream(outputSource);
					
					int c;
					int bytesRead = 0;
					status = 1; //Recording
					while (((c = inputStream.read()) != -1) && !stopped && (duration == -1 || ((System.currentTimeMillis()/1000) < (startTimeInSeconds+duration))))
					{
						fileOutputStream.write(c);
						bytesRead++;
					}
					
					Log.w("Recorder", String.valueOf(bytesRead/1024) + " KBs downloaded.");
					
					fileOutputStream.close();
					
					status = STOPPED;
					//Log.w("Recorder", "finished");
					Recorder.activeRecordings--;
					status = 2; //Ended
				} catch (IOException e){e.printStackTrace();}
			}
		}).start();
	}
	
	int getStatus()
	{
		return status;
	}
	
	void stop()
	{
		stopped = true;
	}
}
