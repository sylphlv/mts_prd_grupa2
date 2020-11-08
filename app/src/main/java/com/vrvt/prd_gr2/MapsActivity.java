package com.vrvt.prd_gr2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapsActivity
        extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted = false;
    private Location lastKnownLocation = null;
    private static final int DEFAULT_ZOOM = 15;
    private LatLng defaultLocation = new LatLng(57.54108, 25.42751);

    private FusedLocationProviderClient fusedLocationProviderClient;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);

        updateLocationUI();
        getDeviceLocation();

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }

        try {
            if (locationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();

                            if (lastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(
                                                lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()
                                        ),
                                        DEFAULT_ZOOM
                                ));

                                try {
                                    Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                                    List<Address> addresses = geocoder.getFromLocation(
                                            lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude(),
                                            1
                                    );
                                    String cityName = addresses.get(0).getLocality();
                                    addNearbyPlaces(cityName);
                                } catch (IOException e) {
                                    Log.e("Exception: %s", e.getMessage(), e);
                                }
                            }
                        } else {
                            Log.d("MAPSACTIVITY", "Current location is null. Using defaults.");
                            Log.e("MAPSACTIVITY", "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void addNearbyPlaces(String cityName) {
        RequestQueue requestQueue = Volley.newRequestQueue(this.getApplicationContext());
        String url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query="
            + cityName + "+city+point+of+interest&language=en&key="
            + getString(R.string.places_api_key);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        addMapMarkers(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(TAG, "error:" + error.toString());
                    }
                });

        requestQueue.add(jsonObjectRequest);
    }

    private void addMapMarkers(JSONObject response) {
        try {
            JSONArray results = response.getJSONArray("results");
            JSONObject visitedPlaces = getVisitedPlaces();

            for (int i = 0; i < results.length(); i++) {
                // Get current json object
                JSONObject place = results.getJSONObject(i);
                JSONObject location = new JSONObject(
                        new JSONObject(place.getString("geometry")).getString("location")
                );

                LatLng newMarker = new LatLng(
                        Double.parseDouble(location.getString("lat")),
                        Double.parseDouble(location.getString("lng"))
                );

                MarkerOptions marker = new MarkerOptions()
                        .position(newMarker)
                        .title(place.getString("name"));

                try {
                    if (visitedPlaces.getBoolean(marker.getTitle())) {
                        marker.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    }
                } catch (JSONException e) {

                }

                mMap.addMarker(marker);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        JSONObject visitedPlaces = getVisitedPlaces();

        try {
            if (visitedPlaces.getBoolean(marker.getTitle()) == true) {
                visitedPlaces.remove(marker.getTitle());
                marker.setIcon(BitmapDescriptorFactory.defaultMarker());
            } else {
                visitedPlaces.put(marker.getTitle(), true);
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            }
        } catch (Exception e) {
            try {
                visitedPlaces.put(marker.getTitle(), true);
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        setVisitedPlaces(visitedPlaces);

        return true;
    }

    private JSONObject getVisitedPlaces() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        JSONObject visitedPlaces;

        try {
            visitedPlaces = new JSONObject(sharedPref.getString("visited_places", ""));
        } catch (Exception e) {
            visitedPlaces = new JSONObject();
        }

        return visitedPlaces;
    }

    private void setVisitedPlaces(JSONObject visitedPlaces) {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("visited_places", visitedPlaces.toString());
        editor.apply();
    }
}