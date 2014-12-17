package com.plugin.gcm;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import java.util.Set;
import java.util.Iterator;
import java.io.StringReader;
import android.util.JsonReader;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";
	
	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
    Log.i(TAG, "No of items in extras = " + extras.size());
    Set<String> extraKeys = extras.keySet();
    Iterator<String> eit = extraKeys.iterator();
    while(eit.hasNext()) {
      String key = eit.next();
      Log.i(TAG, "Current key : " + key);
      Log.i(TAG, key + " : " + extras.getString(key));
    }
		if (extras != null)
		{
			// if we are in the foreground, just surface the payload, else post it to the statusbar
      if (PushPlugin.isInForeground()) {
        extras.putBoolean("foreground", true);
        PushPlugin.sendExtras(extras);
			}
			else {
        extras.putBoolean("foreground", false);

        // Send a notification if there is a message
        if (extras.getString("message") != null && extras.getString("message").length() != 0) {
          createNotification(context, extras);
        } else {
          try {
            String dataString = extras.getString("data");
            if (dataString != null && dataString.length() != 0) {
              // This push is very likely from parse.com. "data" is a JSON string parse it and put the values in 'extras'
              JsonReader reader = new JsonReader(new StringReader(dataString));
              reader.beginObject();
              while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("alert") || key.equals("message") || key.equals("title") || 
                    key.equals("msgcnt") || key.equals("notId")) {
                  extras.putString(key, reader.nextString());
                }
                else {
                  reader.skipValue();
                }
              }
              reader.endObject();
              createNotification(context, extras);
            }
          } catch (Exception e) {
            Log.e(TAG, "Error parsing data field from Parse push : " + e.getMessage());
            Log.e(TAG, "Not creating a system tray notification for the push message");
          }
        }
      }
    }
	}

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}
		
		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setSmallIcon(context.getApplicationInfo().icon)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setTicker(extras.getString("title"))
				.setContentIntent(contentIntent)
				.setAutoCancel(true);

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}
		
		int notId = 0;
		
		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}
		
		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}
	
	private static String getAppName(Context context)
	{
		CharSequence appName = 
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());
		
		return (String)appName;
	}
	
	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

}
