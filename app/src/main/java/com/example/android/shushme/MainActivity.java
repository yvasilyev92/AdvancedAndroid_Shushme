package com.example.android.shushme;

/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.example.android.shushme.provider.PlaceContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        ConnectionCallbacks,
        OnConnectionFailedListener {

    // Constants
    public static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 111;
    private static final int PLACE_PICKER_REQUEST = 1;

    // Member variables
    private PlaceListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private boolean mIsEnabled;
    private GoogleApiClient mClient;
    private Geofencing mGeofencing;

    /**
     * Called when the activity is starting
     *
     * @param savedInstanceState The Bundle that contains the data supplied in onSaveInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.places_list_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PlaceListAdapter(this, null);
        mRecyclerView.setAdapter(mAdapter);



/**
        We create the switch to allow the user to turn the Geofences on or off whenever they want.
         We first initialize the switch state and Handle enable/disable switch change. Then we
        create a Boolean SharedPreferences to remember that state, and to make sure the switch is always
        reflected in the value of that SharedPreference. Then we set the setOnCheckedChangeListener so that
        whenever the switch is turned on off you get an updated SharedPreferences values as well as register
        unregister all Geofences depending on the state of that switch. Notice we also set a private member called
        mIsEnabled and that allows me to keep track of the state of the switch later in the code.
 **/
        Switch onOffSwitch = (Switch) findViewById(R.id.enable_switch);
        mIsEnabled = getPreferences(MODE_PRIVATE).getBoolean(getString(R.string.setting_enabled), false);
        onOffSwitch.setChecked(mIsEnabled);
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
                editor.putBoolean(getString(R.string.setting_enabled), isChecked);
                mIsEnabled = isChecked;
                editor.commit();
                if (isChecked) mGeofencing.registerAllGeofences();
                else mGeofencing.unRegisterAllGeofences();
            }

        });






        // Build up the LocationServices API client
        // Uses the addApi method to request the LocationServices API
        // Also uses enableAutoManage to automatically when to connect/suspend the client
        mClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .enableAutoManage(this, this)
                .build();

        mGeofencing = new Geofencing(this, mClient);

    }














    /***
     * Called when the Google API Client is successfully connected
     *
     * @param connectionHint Bundle of data provided to clients by Google Play services
     */
    @Override
    public void onConnected(@Nullable Bundle connectionHint) {
        refreshPlacesData();
        Log.i(TAG, "API Client Connection Successful!");
    }

    /***
     * Called when the Google API Client is suspended
     *
     * @param cause cause The reason for the disconnection. Defined by constants CAUSE_*.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "API Client Connection Suspended!");
    }

    /***
     * Called when the Google API Client failed to connect to Google Play Services
     *
     * @param result A ConnectionResult that can be used for resolving the error
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.e(TAG, "API Client Connection Failed!");
    }







    /**
     In order to minimize server calls we create the refreshPlacesData method. This method
     gets all Places details in a single call. We start by querying all locally stored Places ID's, then
     create an list of Strings with those ID's to use for the "getPlaceById call.

     Next to fill up this list simply loop over the cursor returned from the query and add each ID to the list.

     Next call getPlaceById and pass in the GoogleApiClient, we also pass in our ArrayList after we've converted it
     into an Array.

     Notice we use member variable "mClient" this means we need to define the Google API client as member variable
     in order to access it here (so we declare it up there).

     Notice the getPlacesById method returns a PendingResult of type PlaceBuffer. PlaceBuffer is a Place API
     object that acts as a list of Places. So to retrieve a server's response we need to set a Callback for this
     PendingResult. To satisfy this we just create a new ResultCall and override the result method inside.

     Now we the server finally replies with the Place's details they'll be include in this PlaceBuffer parameter
     called "places". We also want to update the RecyclerView with new information so we call swapPlaces method
     inserting our new PlaceBuffer.

     And finally to handle registering any new places that get added to the list; inside refreshPlacesData
     we make the Geofences list is up to date by calling updateGeofenceList and then registerAllGeofences
     only if our mIsEnabled switch is enabled.
     */
    public void refreshPlacesData() {


        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
        Cursor data = getContentResolver().query(
                uri,
                null,
                null,
                null,
                null);


        if (data == null || data.getCount() == 0) return;
        List<String> guids = new ArrayList<String>();
        while (data.moveToNext()) {
            guids.add(data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ID)));
        }



        PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mClient,
                guids.toArray(new String[guids.size()]));



        placeResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
            @Override
            public void onResult(@NonNull PlaceBuffer places) {
                mAdapter.swapPlaces(places);
                mGeofencing.updateGeofencesList(places);
                if (mIsEnabled) mGeofencing.registerAllGeofences();
            }
        });
    }














    /***
     * Button Click event handler to handle clicking the "Add new location" Button
     *
     * @param view
     */


    /*
    We want to add our PlacePicker when the AddNewLocation button is clicked.
    So we put our login into onAddPlaceButtonClicked. Now to launch the PlacePicker interface we
    need to create an Intent and start a new Activity. So to do this we use PlacePicker.IntentBuilder
    to create our Intent. Once we have the Intent built, we call startActivityForResult and pass in the Intent
    along with the custom arbitrary request code (1). The startActivityForResult method will launch a new activity
    with the specified Intent and return to the MainActivity eventually when the user either picks a place or
    hits cancel. Now to handle the user's action we need to implement the onActivityResult method.
     */
    public void onAddPlaceButtonClicked(View view) {


        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.need_location_permission_message), Toast.LENGTH_LONG).show();
            return;
        }





        //Important to use a Try-Catch block because if the device doesnt have an up-to-date GooglePlayServices set up,
        //then the app would crash. To handle this appropriately we catch the Exceptions and display a message to user
        //explaining they need to install/update GooglePlayServices in order to continue. However in this Exercise
        //we simply log the messages.




        try {
            // Start a new Activity for the Place Picker API, this will trigger {@code #onActivityResult}
            // when a place is selected or with the user cancels.
            PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
            Intent i = builder.build(this);
            startActivityForResult(i, PLACE_PICKER_REQUEST);
        } catch (GooglePlayServicesRepairableException e) {
            Log.e(TAG, String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e(TAG, String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, String.format("PlacePicker Exception: %s", e.getMessage()));
        }
    }














    /***
     * Called when the Place Picker Activity returns back with a selected place (or after canceling)
     *
     * @param requestCode The request code passed when calling startActivityForResult
     * @param resultCode  The result code specified by the second activity
     * @param data        The Intent that carries the result data.
     */




    /*
    The startActivityForResult method will launch a new activity
    with the specified Intent and return to the MainActivity eventually when the user either picks a place or
    hits cancel. Now to handle the user's action we need to implement the onActivityResult method which is triggered
    once the PlacePicker Activity is complete. Remember that onActivityResult is called when any Activity that was started
    by startActivityForResult is completed. Thats why inside of onActivityForResult we need to check that the
    requestCode is the same one that we used when we started our PlacePicker Activity earlier. So in the case
    that the requestCode matches and the result is "OK" , Then we can find out which the user has selected.
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST && resultCode == RESULT_OK) {
            Place place = PlacePicker.getPlace(this, data);
            if (place == null) {
                Log.i(TAG, "No place selected");
                return;
            }

            // Extract the place information from the API
            String placeName = place.getName().toString();
            String placeAddress = place.getAddress().toString();
            String placeID = place.getId();

            // Insert a new place into DB
            ContentValues contentValues = new ContentValues();
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ID, placeID);
            getContentResolver().insert(PlaceContract.PlaceEntry.CONTENT_URI, contentValues);

            // Get live data information
            refreshPlacesData();
        }
    }















    /*
    To make sure our checkbox is always reflecting the
    correct permission status we need to initialize its state in onResume.
    In case of Android N or later, we need to check if the permission was
    granted using isNotificationPolicyAccessGranted,
    otherwise we could assume that the permission is granted by default.
    Also since we don’t want the user to be unchecking this after permissions
    have been granted, it's best to disable the checkbox once everything seems to be set properly.
    */
    @Override
    public void onResume() {
        super.onResume();




        // Initialize location permissions checkbox
        CheckBox locationPermissions = (CheckBox) findViewById(R.id.location_permission_checkbox);
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissions.setChecked(false);
        } else {
            locationPermissions.setChecked(true);
            locationPermissions.setEnabled(false);
        }




        // Initialize ringer permissions checkbox
        CheckBox ringerPermissions = (CheckBox) findViewById(R.id.ringer_permissions_checkbox);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Check if the API supports such permission change and check if permission is granted
        if (android.os.Build.VERSION.SDK_INT >= 24 && !nm.isNotificationPolicyAccessGranted()) {
            ringerPermissions.setChecked(false);
        } else {
            ringerPermissions.setChecked(true);
            ringerPermissions.setEnabled(false);
        }




    }





    /*
    Now for the last piece of our puzzle we need to actually set the device to silent
    when a Geofence triggers an entry transition and change it back to normal when an
    exit transition is triggered!
     */

    /*
    First let’s add a checkbox to ask the user for RingerMode permissions,
    the onclick event will launch an intent for ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
    and start it.
    This intent launches a new built-in activity that allows the user to turn RingerMode
    permissions on or off for their apps. Once the user turns that on,
    they can then press back and continue to use the app.
     */
    public void onRingerPermissionsClicked(View view) {
        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        startActivity(intent);
    }










    public void onLocationPermissionClicked(View view) {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSIONS_REQUEST_FINE_LOCATION);
    }
}