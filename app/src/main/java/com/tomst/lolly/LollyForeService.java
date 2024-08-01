package com.tomst.lolly;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.tomst.lolly.core.BoundServiceListener;
import com.tomst.lolly.core.TDevState;
import com.tomst.lolly.core.TInfo;
import com.tomst.lolly.core.TMSReader;



public class LollyForeService extends Service {

    private BoundServiceListener mListener;
    private Context mContext;

    private static final String CHANNEL_ID = "ForegroundLollyChannel";
    NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private int progress = 0;
    private TMSReader ftTMS;

    // communication with the other parts of the app, see below
    private Handler dataHandler;  // to transfer data from device into HomeFragment
    private static Handler infoHandler = null;  // to transfer state machine position from the TMSReader

    public void SetDataHandler(Handler han) {this.dataHandler=han;}


    private void sendDataProgress(TDevState stat, int pos) { // Handle sending message back to handler
        Message message = infoHandler.obtainMessage();
        TInfo info = new TInfo();
        info.stat  = stat;
        info.msg   = String.valueOf(pos);
        info.idx = pos;  // progress bar position
        message.obj = info;
        infoHandler.sendMessage(message);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        infoHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String input = intent.getStringExtra("inputExtra");

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Progress Foreground Service")
                    .setContentText("Service is running")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setProgress(100, 0, false)
                    .setSound(null);

        startForeground(1,notificationBuilder.build());

        progress = 1;
        simulateProgress();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    private void drawProgress() {
        notificationBuilder.setProgress(100, progress, false);
        notificationManager.notify(1, notificationBuilder.build());
    }


    private void simulateProgress() {
        /*
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (progress < 100) {
                    progress += 10;
                    notificationBuilder.setProgress(100, progress, false);
                    notificationManager.notify(1, notificationBuilder.build());
                } else {
                    stopForeground(true);
                    stopSelf();
                }
            }
        }, 1000);
         */
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            //Log.e("Service", "Service is running...");
                            drawProgress();
                            progress++;
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        ).start();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setSound(null,null); // Disable sound
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(serviceChannel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public LollyForeService() {
        mContext = null;
    }

    private final IBinder binder = new LollyForeService.LollyBinder();
    public static final String PERMISSION_STRING
            = android.Manifest.permission.ACCESS_FINE_LOCATION;
    private LocationListener listener;
    private LocationManager locManager;

    public class LollyBinder extends Binder {
        public LollyForeService getOdometer() {
            return LollyForeService.this;  // vraci odkaz na instanci tridy
        }

        public void setListener(BoundServiceListener listener){
            mListener = listener;
        }
    }


    public void startBindService(){

        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job

        /*
        if (mContext == null){
            throw new UnsupportedOperationException("startBindService.mContext is null / (set app context !)");
        }
        //Context context = getContext();
        SharedPreferences sharedPref = mContext.getSharedPreferences(getString(R.string.save_options), mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();


        ftTMS = new TMSReader(mContext);
        ftTMS.ConnectDevice();
        ftTMS.SetHandler(handler);
        ftTMS.SetDataHandler(this.dataHandler);

        ftTMS.SetRunning(true); // povol provoz v mLoop
        //ftTMS.start();

        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = 12;
        serviceHandler.sendMessage(msg);
         */
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        infoHandler.removeCallbacksAndMessages(null);
    }

}
