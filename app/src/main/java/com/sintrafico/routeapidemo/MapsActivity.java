package com.sintrafico.routeapidemo;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.net.Uri.Builder;
import android.util.Log;


import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.geojson.GeoJsonLayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, OnMapClickListener {

    private GoogleMap mMap;
    private final String apiKey = "testKeyHash";
    private Marker startMarker, endMarker;
    GeoJsonLayer layer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add a marker in MX City and move the camera
        LatLng start = new LatLng(19.4602, -99.15046);
        LatLng end = new LatLng(19.42712,-99.16659);
        MarkerOptions options = new MarkerOptions();
        options.draggable(true);
        startMarker = mMap.addMarker(options.position(start).title("Inicio"));
        endMarker = mMap.addMarker(options.position(end).title("Final"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 14));
        updateRoute(start, end);

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                LatLng newStart = new LatLng(location.getLatitude(),location.getLongitude());
                startMarker.setPosition(newStart);
                updateRoute(newStart, endMarker.getPosition());
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        try {
            // Register the listener with the Location Manager to receive location updates
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2 * 60 * 1000, 10, locationListener);
        } catch (SecurityException e){
            Log.e("MapsActivity", "Security Error " + e.toString());
        }
        mMap.setOnMapClickListener(this);
    }

    @Override
    public void onMapClick(LatLng point) {
        endMarker.setPosition(point);
        updateRoute(startMarker.getPosition(), point);
    }

    public void updateRoute(LatLng start, LatLng end){
        RouteAPITask api = new RouteAPITask();
        Builder b = new Builder();
        Builder builder = new Builder();
        builder.scheme("http")
                .authority("api.sintrafico.com")
                .appendPath("route")
                .appendQueryParameter("start", String.valueOf(start.latitude)+","+String.valueOf(start.longitude))
                .appendQueryParameter("end", String.valueOf(end.latitude)+","+String.valueOf(end.longitude))
                .appendQueryParameter("transport","car")
                .appendQueryParameter("key", apiKey);
        try {
            URL myUrl = new URL(builder.build().toString());
            api.execute(myUrl);
        } catch(MalformedURLException e){
            Log.e("MapsActivity", "URL Error " + e.toString());
        }

    }

    private class RouteAPITask  extends AsyncTask<URL, Void, JSONObject> {
        protected JSONObject doInBackground(URL... urls) {
            int count = urls.length;
            String json = "";
            for (int i = 0; i < count; i++) {
                try {
                    HttpURLConnection con = (HttpURLConnection) urls[i].openConnection();
                    if (con.getResponseCode() == 201) { // Modo "poleo"
                        String response = readResponse(con);
                        Log.i("RouteAPITask", "Result:" + response.toString());
                    /*  TODO: volver a llamar el API hasta obtener respuesta 200
                        solo aplica para algunos requests, los requests en este Demo no lo necesitan.
                     */
                    }else if (con.getResponseCode() != 200) {
                        Log.e("RouteAPITask", "HTTP error code : " + con.getResponseCode() + "\nQuery:" + urls[i].toString());
                    } else {
                        try {
                            String response = readResponse(con);
                            Log.i("RouteAPITask", "Result:" + response);
                            return new JSONObject(response);
                        } catch (Exception e) {
                            Log.e("RouteAPITask", "Error converting result " + e.toString());
                        }
                    }
                } catch (IOException e) {
                    Log.e("RouteAPITask", "Error converting result " + e.toString());
                }
            }
            return null;
        }

        protected void onPostExecute(JSONObject result) {
            try {
                JSONArray routes = result.getJSONArray("routes");
                JSONObject geometry = routes.getJSONObject(0).getJSONObject("geometry");
                if (layer != null){
                    layer.removeLayerFromMap();
                }
                layer = new GeoJsonLayer(mMap, geometry);
                layer.getDefaultLineStringStyle().setColor(Color.RED);
                layer.addLayerToMap();
            } catch (JSONException e) {
                Log.e("RouteAPITask", "Error " + e.toString());
            }
        }

        public String readResponse(HttpURLConnection connection) throws IOException{
            BufferedReader reader =  new BufferedReader(new InputStreamReader((connection.getInputStream())));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            return sb.toString();
        }
    }
}
