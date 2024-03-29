package com.mahfuznow.iotree.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.github.lzyzsd.circleprogress.ArcProgress;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mahfuznow.iotree.R;
import com.mahfuznow.iotree.model.DeviceDataModel;
import com.mahfuznow.iotree.util.ConnectivityUtils;

import java.util.Objects;

public class DeviceControlInternetActivity extends AppCompatActivity {
    TextView tv_device_name, tv_device_mac_address;
    ArcProgress temperature_progress, moisture_progress;
    SwitchCompat switch_pump;
    ProgressBar progress_switch_pump;
    LinearLayout ll_overlay_progress;

    FirebaseDatabase database;
    DatabaseReference myRef;
    Boolean device_found_from_internet = false;
    private String TAG = DeviceControlInternetActivity.class.getSimpleName();

    String s_device_name, s_device_password;
    private Context context = DeviceControlInternetActivity.this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control_internet);
        Objects.requireNonNull(getSupportActionBar()).setHomeButtonEnabled(true);

        tv_device_mac_address = findViewById(R.id.tv_device_mac_address);
        tv_device_name = findViewById(R.id.tv_device_name);
        temperature_progress = findViewById(R.id.temperature_progress);
        moisture_progress = findViewById(R.id.moisture_progress);
        switch_pump = findViewById(R.id.switch_pump);
        progress_switch_pump = findViewById(R.id.progress_switch_pump);
        ll_overlay_progress = findViewById(R.id.ll_overlay_progress);


        s_device_name = Objects.requireNonNull(getIntent().getExtras()).getString("s_device_name");
        s_device_password = Objects.requireNonNull(getIntent().getExtras()).getString("s_device_password");
        try {
            Objects.requireNonNull(getSupportActionBar()).setTitle(s_device_name);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (Exception ignored) {
        }

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference(s_device_name + "/" + s_device_password);

        //starting a timer for checking timeout
        new CountDownTimer(15000, 1000) {
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                if (ll_overlay_progress.getVisibility() == View.VISIBLE) {
                    finishWithResult(RESULT_CANCELED, getString(R.string.timeout));
                }
            }
        }.start();


        // Read from the database
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                if (dataSnapshot.getValue() != null) {
                    device_found_from_internet = true;
                    DeviceDataModel mDeviceDataModel = dataSnapshot.getValue(DeviceDataModel.class);
                    //Log.d(TAG, "Value is: " + firebaseModel.getMac()+firebaseModel.getMoisture());
                    tv_device_name.setText(s_device_name);
                    if (mDeviceDataModel != null) {
                        tv_device_mac_address.setText(mDeviceDataModel.getMac_address());
                        temperature_progress.setProgress((int) Float.parseFloat(mDeviceDataModel.getTemperature()));
                        moisture_progress.setProgress(Integer.parseInt(mDeviceDataModel.getMoisture()));
                        switch_pump.setChecked(Boolean.parseBoolean(mDeviceDataModel.getPump_trig()));
                    }
                    //disabling overlay progress
                    ll_overlay_progress.setVisibility(View.GONE);
                } else {
                    finishWithResult(RESULT_CANCELED, getString(R.string.no_device_found));
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                finish();

            }
        });

        switch_pump.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton compoundButton, final boolean b) {
                if (ConnectivityUtils.isConnected(context)) {
                    //
                    switch_pump.setVisibility(View.GONE);
                    progress_switch_pump.setVisibility(View.VISIBLE);
                    myRef.child("pump_trig").setValue(String.valueOf(b))
                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    switch_pump.setVisibility(View.VISIBLE);
                                    progress_switch_pump.setVisibility(View.GONE);

                                    Snackbar snackbar = Snackbar.make(compoundButton, "Pump has been turned " + (b ? "ON" : "OFF"), Snackbar.LENGTH_SHORT);
                                    snackbar.setAction("Action", null);
                                    snackbar.getView().setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary));
                                    snackbar.show();
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Snackbar.make(compoundButton, "Failed", Snackbar.LENGTH_SHORT)
                                            .setAction("Action", null)
                                            .show();
                                    switch_pump.toggle();
                                }
                            })
                    ;
                } else {
                    switch_pump.toggle();
                    Snackbar.make(compoundButton, "No internet Connection", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null)
                            .show();

                }
            }
        });
    }

    private void finishWithResult(int result_code, String msg) {
        Intent intent = new Intent();
        intent.putExtra("msg", msg);
        setResult(result_code, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        //resulting back to the previous activity
        if (device_found_from_internet) {
            finishWithResult(RESULT_OK, "");
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (device_found_from_internet) {
            //TODO: save device name and password into shared pref for next time user tries to connect
        }
        super.onDestroy();
    }
}