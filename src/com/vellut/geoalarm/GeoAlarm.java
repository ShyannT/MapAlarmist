package com.vellut.geoalarm;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationProvider;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationClient.OnAddGeofencesResultListener;
import com.google.android.gms.location.LocationClient.OnRemoveGeofencesResultListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vellut.geoalarm.io.LatLngBoundsDeserializer;
import com.vellut.geoalarm.io.LatLngBoundsSerializer;

public class GeoAlarm {

	public boolean isFirstTimeRun;
	public boolean isAlarmOn;
	public Uri ringtoneUri;
	public LatLngBounds zone;
	public boolean isUseVibrate;
	public ArrayList<SavedLocation> savedLocations;
	public String locationTechnique;
	public boolean isInZone;
	public long alarmSetTime;

	private GsonBuilder builder;

	public GeoAlarm() {
		this.savedLocations = new ArrayList<SavedLocation>();

		builder = new GsonBuilder();
		builder.registerTypeAdapter(LatLngBounds.class,
				new LatLngBoundsSerializer());
		builder.registerTypeAdapter(LatLngBounds.class,
				new LatLngBoundsDeserializer());
	}

	public void restorePreferences(Context context) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		isFirstTimeRun = settings.getBoolean(
				GeoAlarmUtils.PREF_IS_FIRST_TIME_RUN, true);

		if (!isFirstTimeRun) {
			Log.d(GeoAlarmUtils.APPTAG, "Not First Time Run");
			double north = getDouble(settings.getString(
					GeoAlarmUtils.PREF_NORTH_LAT, null));
			double south = getDouble(settings.getString(
					GeoAlarmUtils.PREF_SOUTH_LAT, null));
			double west = getDouble(settings.getString(
					GeoAlarmUtils.PREF_WEST_LON, null));
			double east = getDouble(settings.getString(
					GeoAlarmUtils.PREF_EAST_LON, null));
			zone = new LatLngBounds(new LatLng(south, west), new LatLng(north,
					east));
		}

		if (isFirstTimeRun) {
			// Default alarm sound
			Uri defaultUri = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_ALARM);
			if (defaultUri == null) {
				defaultUri = RingtoneManager
						.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				if (defaultUri == null) {
					defaultUri = RingtoneManager
							.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
				}
			}
			ringtoneUri = defaultUri;
			locationTechnique = context
					.getString(R.string.pref_location_technique_defaultvalue);
			isInZone = false;
			alarmSetTime = 0;
		} else {
			String sRingtoneUri = settings.getString(
					GeoAlarmUtils.PREF_RINGTONE_URI, null);
			if (sRingtoneUri == null) {
				ringtoneUri = null;
			} else {
				ringtoneUri = Uri.parse(sRingtoneUri);
			}

			String sSavedLocations = settings.getString(
					GeoAlarmUtils.PREF_SAVED_LOCATIONS, null);
			if (sSavedLocations != null) {
				try {
					savedLocations = deserializeFromString(sSavedLocations);
				} catch (Exception ex) {
					Log.e(GeoAlarmUtils.APPTAG,
							"Error getting saved locations", ex);
				}
			}

		}

		isUseVibrate = settings.getBoolean(GeoAlarmUtils.PREF_USE_VIBRATE,
				false);

		isAlarmOn = settings.getBoolean(GeoAlarmUtils.PREF_IS_ALARM_ON, false);

		locationTechnique = settings
				.getString(
						GeoAlarmUtils.PREF_LOCATION_TECHNIQUE,
						context.getString(R.string.pref_location_technique_defaultvalue));
		
		isInZone = settings.getBoolean(GeoAlarmUtils.PREF_IS_IN_ZONE, false);
		alarmSetTime = settings.getLong(GeoAlarmUtils.PREF_ALARM_SET_TIME, 0);
	}

	public void reloadPreferenceForKey(Context context, String key) {
		if (key.equals(GeoAlarmUtils.PREF_LOCATION_TECHNIQUE)) {
			locationTechnique = PreferenceManager.getDefaultSharedPreferences(
					context).getString(key, "");
		}
	}

	private double getDouble(String sDouble) {
		if (sDouble == null) {
			return Double.NaN;
		} else {
			return Double.parseDouble(sDouble);
		}
	}

	public void savePreferences(Context context) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = settings.edit();

		editor.putBoolean(GeoAlarmUtils.PREF_IS_FIRST_TIME_RUN, isFirstTimeRun);
		editor.putString(GeoAlarmUtils.PREF_NORTH_LAT,
				Double.toString(zone.northeast.latitude));
		editor.putString(GeoAlarmUtils.PREF_SOUTH_LAT,
				Double.toString(zone.southwest.latitude));
		editor.putString(GeoAlarmUtils.PREF_WEST_LON,
				Double.toString(zone.southwest.longitude));
		editor.putString(GeoAlarmUtils.PREF_EAST_LON,
				Double.toString(zone.northeast.longitude));
		if (ringtoneUri != null) {
			editor.putString(GeoAlarmUtils.PREF_RINGTONE_URI,
					ringtoneUri.toString());
		} else {
			editor.putString(GeoAlarmUtils.PREF_RINGTONE_URI, null);
		}
		editor.putBoolean(GeoAlarmUtils.PREF_USE_VIBRATE, isUseVibrate);
		editor.putBoolean(GeoAlarmUtils.PREF_IS_ALARM_ON, isAlarmOn);
		editor.putString(GeoAlarmUtils.PREF_SAVED_LOCATIONS,
				serializeToString(savedLocations));
		editor.putLong(GeoAlarmUtils.PREF_ALARM_SET_TIME, alarmSetTime);

		// no save for locationTechnique : managed by PreferenceManager
		
		editor.commit();
	}
	
	public void saveLocationZone(Context context) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = settings.edit();
		
		editor.putBoolean(GeoAlarmUtils.PREF_IS_IN_ZONE, isInZone);
		
		editor.commit();
	}

	private String serializeToString(ArrayList<SavedLocation> locations) {
		Gson gson = builder.create();
		Type type = new TypeToken<ArrayList<SavedLocation>>() {
		}.getType();
		String ser = gson.toJson(locations, type);
		return ser;
	}

	private ArrayList<SavedLocation> deserializeFromString(String json) {
		Gson gson = builder.create();
		Type type = new TypeToken<ArrayList<SavedLocation>>() {
		}.getType();
		Object obj = gson.fromJson(json, type);
		return (ArrayList<SavedLocation>) obj;
	}
	
	public int getLocationPriority(Context context) {
		if(locationTechnique.equals(context.getString(GeoAlarmUtils.LOCATION_TECHNIQUE_LOW_POWER))) {
			return LocationRequest.PRIORITY_LOW_POWER;
		} else if(locationTechnique.equals(context.getString(GeoAlarmUtils.LOCATION_TECHNIQUE_BALANCED_POWER))) {
			return LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
		} else if(locationTechnique.equals(context.getString(GeoAlarmUtils.LOCATION_TECHNIQUE_HIGH_ACCURACY))) {
			return LocationRequest.PRIORITY_HIGH_ACCURACY;
		} else {
			Log.d(GeoAlarmUtils.APPTAG, "Should not happen: No Power");
			return LocationRequest.PRIORITY_NO_POWER;
		}
	}
	
	public void setAlarm(Context context, LocationClient locationClient,
			OnAddGeofencesResultListener listener) {
		/*
		 * PendingIntent transitionPendingIntent =
		 * getTransitionPendingIntent(context); List<Geofence> geofences = new
		 * ArrayList<Geofence>(); geofences.add(buildGeofence());
		 * locationClient.addGeofences(geofences, transitionPendingIntent,
		 * listener);
		 */

		AlarmManager am = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		PendingIntent alarmWakeUpPendingIntent = getAlarmWakeUpPendingIntent(context);
		am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 2 * 60 * 1000,
				2 * 60 * 1000, alarmWakeUpPendingIntent);
		alarmSetTime = SystemClock.elapsedRealtime();
	}

	public void disableAlarm(Context context, LocationClient locationClient,
			OnRemoveGeofencesResultListener listener) {
		/*
		 * List<String> geofenceIds = new ArrayList<String>();
		 * geofenceIds.add(GeoAlarmUtils.GEOFENCE_REQUEST_ID);
		 * locationClient.removeGeofences(geofenceIds, listener);
		 */

		AlarmManager am = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		PendingIntent alarmWakeUpPendingIntent = getAlarmWakeUpPendingIntent(context);
		am.cancel(alarmWakeUpPendingIntent);
	}

	private PendingIntent getTransitionPendingIntent(Context context) {
		Intent intent = new Intent(context,
				ReceiveTransitionsBroadcastReceiver.class);
		if (ringtoneUri != null) {
			intent.putExtra(GeoAlarmUtils.EXTRA_RINGTONE_URI,
					ringtoneUri.toString());
		}
		intent.putExtra(GeoAlarmUtils.EXTRA_USE_VIBRATE, isUseVibrate);
		intent.putExtra(GeoAlarmUtils.EXTRA_ALARM_SET_TIME,
				SystemClock.elapsedRealtime());

		return PendingIntent.getBroadcast(context, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private PendingIntent getAlarmWakeUpPendingIntent(Context context) {
		Intent intent = new Intent(context, AlarmWakeUpBroadcastReceiver.class);
		return PendingIntent.getBroadcast(context, 0, intent, 0);
	}

	private Geofence buildGeofence() {
		LatLng center = zone.getCenter();
		double dLng = Math.abs(zone.northeast.longitude
				- zone.southwest.longitude);
		double dLat = Math.abs(zone.northeast.latitude
				- zone.southwest.latitude);
		float radius = (float) Math.min(dLng, dLat);
		radius *= 111320; // Length of a degree in meter at equator

		Log.d(GeoAlarmUtils.APPTAG, "Center " + center.latitude + " "
				+ center.longitude + " " + radius);

		return new Geofence.Builder()
				.setRequestId(GeoAlarmUtils.GEOFENCE_REQUEST_ID)
				.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
				.setExpirationDuration(Geofence.NEVER_EXPIRE)
				.setCircularRegion(center.latitude, center.longitude, radius)
				.build();
	}

}
