package com.example.android.shushme;

/**
 * Created by Yevgeniy on 8/19/2017.
 */

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;

import java.util.ArrayList;
import java.util.List;
/**
In Exercise 4, the goal is to create a Geofence for each place we've added in the database,
and then register those Geofences and start listening to any entry or exit that triggers. We put
all this logic into the Geofencing clas.
**/
/**
First we need to build Geofence objects using their longitute and latitutdes and a predefined radius.
Then we'll store all these Geofences in a list and then register those Geofences. In order to register
a Geofence we must create a Geofence Request object from that list of Geofences. We'll also need a PendingIntent
that will specify which Intent to launch when the Geofence entry or exit event triggers. In our case we'll
be using a BroadcastReceiver in our PendingIntent. Which means that whenever a device enters or exits any of
the Geofences then the onReceive method in BR will run.
**/
/**
Once we have those components ready, we will start passing in our Geofence Request and PendingIntent
to Google Play Services. Along with them we'll add in our GoogleAPIClient . With all those 3 we will
be able to walk our device into any of our Geofences created.
**/


public class Geofencing implements ResultCallback {



    // Constants
    public static final String TAG = Geofencing.class.getSimpleName();
    private static final float GEOFENCE_RADIUS = 50; // 50 meters
    private static final long GEOFENCE_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours




    private List<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;





    public Geofencing(Context context, GoogleApiClient client) {
        mContext = context;
        mGoogleApiClient = client;
        mGeofencePendingIntent = null;
        mGeofenceList = new ArrayList<>();
    }








    /***
     * Registers the list of Geofences specified in mGeofenceList with Google Place Services
     * Uses {@code #mGoogleApiClient} to connect to Google Place Services
     * Uses {@link #getGeofencingRequest} to get the list of Geofences to be registered
     * Uses {@link #getGeofencePendingIntent} to get the pending intent to launch the IntentService
     * when the Geofence is triggered
     * Triggers {@link #onResult} when the geofences have been registered successfully
     */

    /*
    We create public method registerAllGeofences we register our Geofences. We start with a
    check to make sure our GoogleAPIClient is actually connected, and that we actually have some
    Geofences to register. Then we can call LocationServices.GeofrencingApi.addGeofences passing in the
    client and the results of our two helper methods. We set the callback result to "this" which means we'll
    have to implement the ResultCallback interface and override onResult.
    */
    public void registerAllGeofences() {
        // Check that the API client is connected and that the list has Geofences in it
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected() ||
                mGeofenceList == null || mGeofenceList.size() == 0) {
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }









    /***
     * Unregisters all the Geofences created by this app from Google Place Services
     * Uses {@code #mGoogleApiClient} to connect to Google Place Services
     * Uses {@link #getGeofencePendingIntent} to get the pending intent passed when
     * registering the Geofences in the first place
     * Triggers {@link #onResult} when the geofences have been unregistered successfully
     */

    /*
    We also need an unRegisterAllGeofences. This doesnt require a Geofencing Request. Here we just
    use LocationServices.GeofencingApi.removeGeofences to remove all Geofences created using the specified
    PendingIntent.
     */
    public void unRegisterAllGeofences() {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
            return;
        }
        try {
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    // This is the same pending intent that was used in registerGeofences
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }














    /***
     * Updates the local ArrayList of Geofences using data from the passed in list
     * Uses the Place ID defined by the API as the Geofence object Id
     *
     * @param places the PlaceBuffer result of the getPlaceById call
     */

    /**
     The updateGeofencesList method, given a PlaceBuffer instance, it will go through
     all the places in it and for each place it will create a Geofences object and add
     it to a list.
    **/
    public void updateGeofencesList(PlaceBuffer places) {
        mGeofenceList = new ArrayList<>();
        if (places == null || places.getCount() == 0) return;
        for (Place place : places) {
            // Read the place information from the DB cursor
            String placeUID = place.getId();
            double placeLat = place.getLatLng().latitude;
            double placeLng = place.getLatLng().longitude;
            // Build a Geofence object
            Geofence geofence = new Geofence.Builder()
                    .setRequestId(placeUID) //set its unique ID
                    .setExpirationDuration(GEOFENCE_TIMEOUT) //set expiration to 24hours
                    .setCircularRegion(placeLat, placeLng, GEOFENCE_RADIUS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();
            // Add it to the list
            mGeofenceList.add(geofence);
        }
    }









    /***
     * Creates a GeofencingRequest object using the mGeofenceList ArrayList of Geofences
     * Used by {@code #registerGeofences}
     *
     * @return the GeofencingRequest object
     */

    /*

    getGeofencingRequest is private helper method which uses to GeofencingRequest.Builder method
    to build up our Geofencing Request. When builing a Geofencing Request, you need to specify something
    called an initial trigger. So this trigger defines what happens if the device is already inside any of
    the Geofences that we're about to register.
    We set ours to INITIAL_TRIGGER_ENTER which basically means if a device is already inside a Geofence at the time
    of registering, then trigger an entry transition event immediately.
    To complete the Geofencing Request we add the Geofences from the ArrayList we created and then we build it.
    */
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }







    /***
     * Creates a PendingIntent object using the GeofenceTransitionsIntentService class
     * Used by {@code #registerGeofences}
     *
     * @return the PendingIntent object
     */

    /*
    We need to create the helper method getGeofencePendingIntent that will create our PendingIntent
    for us. First we create an intent for a GeofenceBroadcastReceiver class. Then we use the method
    PendingIntent.getBroadcast is used to create the PendingIntent.
    */
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(mContext, GeofenceBroadcastReceiver.class);
        mGeofencePendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }










    //We just log an error message.
    @Override
    public void onResult(@NonNull Result result) {
        Log.e(TAG, String.format("Error adding/removing geofence : %s",
                result.getStatus().toString()));
    }

}