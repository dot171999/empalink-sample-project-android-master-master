package com.empatica.sample;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class SensorService extends Service implements EmpaDataDelegate, EmpaStatusDelegate {

    public int counter=0;
    private EmpaDeviceManager deviceManager = null;

    private float Bvp=0,Gsr=0,Ibi=0,Temp=0;

    private String Bvp1,Gsr1,Ibi1,Temp1;

    private static final String EMPATICA_API_KEY = "39eea0ea609b400ea4785c8f2e0a33f8"; // TODO insert your API Key here

    public SensorService(Context applicationContext) {
        super();
        Log.i("######HERE", "context created");
    }

    public SensorService() {
    }

    String ipAddress = "noIP";

    @Override
    public void onCreate() {
        super.onCreate();
        //SharedPreferences pref = getApplicationContext().getSharedPreferences("ipSP", 0);

        SharedPreferences preferences = getSharedPreferences("SaveData",Context.MODE_PRIVATE);
        String name = preferences.getString("key", "defaultValue");
        ipAddress = name;
        Log.e("IP from SP",name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else{
            startForeground(1, new Notification());
        }

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
    }

    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.empatica.sample";
        String channelName = "My Background Service";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        }
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Empatica e4 is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //startTimer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("EXIT", "ondestroy!");
        deviceManager.disconnect();
        deviceManager.cleanUp();
        stoptimertask();

        Intent broadcastIntent = new Intent(this, SensorRestarterBroadcastReceiver.class);
        sendBroadcast(broadcastIntent);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    ///////////////////

    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, to wake up every 1 second
        timer.schedule(timerTask, 100, 100); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i("in timer", "in timer ++++  "+ (counter++));
                sendWorkPostRequest2();
                updateLabel(Bvp, Gsr, Ibi, Temp, Bvp1, Gsr1, Ibi1, Temp1);
            }
        };
    }

    public void stoptimertask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void sendWorkPostRequest2(){
        try {
            Log.e("E4sendingToServer","ip "+ipAddress);
            RequestQueue requestQueue = Volley.newRequestQueue(SensorService.this);
            String URL = "http://"+ipAddress+":3000";
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("event", "EmpaticaE4");
            jsonBody.put("value", "E4working");
            final String requestBody = jsonBody.toString();

            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.i("VOLLEY", response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("VOLLEY", error.toString());
                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return requestBody == null ? null : requestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                        return null;
                    }
                }
            };

            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                    10000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            requestQueue.add(stringRequest);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////

    @Override
    public void didRequestEnableBluetooth() {
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        //Log.i("#################", "3");
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);

            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                //Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        //Log.i("#################", "6");
        // Update the UI
        //updateLabel(statusLabel, status.name());

        if (status == EmpaStatus.READY) {
            //updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            Log.i("#################", "status ready start scanning");
            deviceManager.startScanning();
            // The device manager has established a connection
        }
        // The device manager is ready for use
    }

    //##########################

    String timeStamp(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String format = simpleDateFormat.format(new Date());
        return format;
    }

    @Override
    public void didEstablishConnection() {
        Log.i("#################", "connection made with e4 ");
        startTimer();
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        Bvp =bvp;
        Bvp1=timeStamp();
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        Gsr=gsr;
        Gsr1=timeStamp();
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        Ibi=ibi;
        Ibi1=timeStamp();
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        Temp=temp;
        Temp1=timeStamp();
    }



    public void updateLabel(float Bvp, float Gsr, float Ibi, float Temp, String Bvp1, String Gsr1, String Ibi1, String Temp1) {
        Log.i ("#######################", "written");
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "EmpaticaX");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "E4log"+".txt");
            FileWriter writer = new FileWriter(gpxfile,true);
            writer.append(" Bvp = "+Bvp+" TimeBvp = "+Bvp1+"  " +
                    " Gsr = "+Gsr+" TimeGsr = "+Gsr1+"  " +
                    " Ibi = "+Ibi+" TimeIbi = "+Ibi1+"  " +
                    " Temp = "+Temp+" TimeTemp = "+Temp1);
            writer.append("\n\r");
            writer.flush();
            writer.close();
            //Toast toast= Toast.makeText(context,
            //"Logged", Toast.LENGTH_SHORT);
            //toast.setGravity(Gravity.BOTTOM|Gravity.END, 0, 0);
            //toast.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
    }

    @Override
    public void didReceiveTag(double timestamp) {
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
    }

}

