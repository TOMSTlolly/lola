package com.tomst.lolly;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static java.sql.DriverManager.println;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Firebase;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.FirebaseDatabase;
import com.tomst.lolly.core.Constants;
import com.tomst.lolly.core.FileOpener;
import com.tomst.lolly.core.PermissionManager;
import com.tomst.lolly.databinding.ActivityMainBinding;
import com.tomst.lolly.core.DmdViewModel;
import com.tomst.lolly.ui.options.OptionsFragment;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;



public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final int STORAGE_PERMISSION_CODE = 100;

    private View view;
    private ActivityMainBinding binding;

  //  private final String TAG = "TOMST";

    private final FileOpener fopen;

    private DmdViewModel dmdViewModel;

    private LollyService lolly;


    public MainActivity(){
        fopen = new FileOpener(this);
    }


    private ServiceConnection connection = new ServiceConnection() {
        private boolean bound;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            LollyService.LollyBinder lollyBinder =
                    (LollyService.LollyBinder) binder;
            lolly = lollyBinder.getOdometer();
            bound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bound = false;
        }
    };


    public boolean checkPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            return Environment.isExternalStorageManager();
        }
        else{
            //Android is below 11(R)
            int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

            return write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED;
        }
    }


    @Override
    public void onClick(View v) {

        if (!checkPermission()) {
            Snackbar.make(view,"requesting permission", Snackbar.LENGTH_LONG).show();
            requestPermission();
        }

    }

    private void requestPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            try {
                Log.d(Constants.TAG, "requestPermission: try");

                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", this.getPackageName(), null);
                intent.setData(uri);
                storageActivityResultLauncher.launch(intent);
            }
            catch (Exception e){
                Log.e(Constants.TAG, "requestPermission: catch", e);
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                storageActivityResultLauncher.launch(intent);
            }
        }
        else {
            //Android is below 11(R)
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE
            );
        }
    }

    private void createFolder(){
        //get folder name
        String folderName = "test"; //folderNameEt.getText().toString().trim();

        //create folder using name we just input
        File file = new File(Environment.getExternalStorageDirectory() + "/" + folderName);
        //create folder
        boolean folderCreated = file.mkdir();

        //show if folder created or not
        if (folderCreated) {
            Toast.makeText(this, "Folder Created....\n" + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Folder not created...", Toast.LENGTH_SHORT).show();
        }

    }


    private ActivityResultLauncher<Intent> storageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    Log.d(Constants.TAG, "onActivityResult: ");
                    //here we will handle the result of our intent
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                        //Android is 11(R) or above
                        if (Environment.isExternalStorageManager()){
                            //Manage External Storage Permission is granted
                            Log.d(Constants.TAG, "onActivityResult: Manage External Storage Permission is granted");
                            createFolder();
                        }
                        else{
                            //Manage External Storage Permission is denied
                            Log.d(Constants.TAG, "onActivityResult: Manage External Storage Permission is denied");
                            Toast.makeText(MainActivity.this, "Manage External Storage Permission is denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {
                        //Android is below 11(R)
                    }
                }
            }
    );

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE){
            if (grantResults.length > 0){
                //check each permission if granted or not
                boolean write = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean read = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (write && read){
                    //External Storage permissions granted
                    Log.d(Constants.TAG, "onRequestPermissionsResult: External Storage permissions granted");
                    createFolder();
                }
                else{
                    //External Storage permission denied
                    Log.d(Constants.TAG, "onRequestPermissionsResult: External Storage permission denied");
                    Toast.makeText(this, "External Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /*
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean locationAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean cameraAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (locationAccepted && cameraAccepted)
                        Snackbar.make(view, "Permission Granted, Now you can access external storage.", Snackbar.LENGTH_LONG).show();
                    else {

                        Snackbar.make(view, "Permission Denied, You cannot access external storage.", Snackbar.LENGTH_LONG).show();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (shouldShowRequestPermissionRationale(READ_EXTERNAL_STORAGE)) {
                                showMessageOKCancel("You need to allow access to both the permissions",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    requestPermissions(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE},
                                                            PERMISSION_REQUEST_CODE);
                                                }
                                            }
                                        });
                                return;
                            }
                        }

                    }
                }


                break;
        }
    }
     */

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    // for user authentication
    FirebaseAuth auth;
    FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        view = binding.getRoot();

        // remove stupid line on bottom of action bar
        getSupportActionBar().setElevation(0);

        // for user authentication
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();

        /*
        if (user == null)
        {
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
            finish();
        }
        */

        // permissionManager = new PermissionManager(this);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (action != null)
        {
            switch (intent.getAction()) {
                case Intent.ACTION_GET_CONTENT:
                    fopen.isRequestDocument = true;
                    setResult(RESULT_CANCELED);
                    break;
                case Intent.ACTION_OPEN_DOCUMENT: {
                    fopen.isRequestDocument = true;
                    setResult(RESULT_CANCELED);
                    break;
                }
                default:
                    fopen.isRequestDocument = false;
            }
        }

        //checkPermission();
        if (!checkPermission()) {
            requestPermission();
        }

        // sdileny datovy modul
        dmdViewModel = new ViewModelProvider(this).get(DmdViewModel.class);

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_graph, R.id.navigation_notifications, R.id.navigation_options)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

    }

}