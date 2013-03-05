package com.taxisoft.remotedialer;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.TextChange;
import com.googlecode.androidannotations.annotations.ViewById;

import android.os.Bundle;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

@EActivity(R.layout.activity_settings)
public class SettingsActivity extends Activity
{
	@ViewById
	EditText edtName;
	@ViewById
	CheckBox cbAutostart;
	@ViewById
	AutoCompleteTextView atvDefaultDevice;
	
	SharedPreferences mSettings;
	Timer mDeviceNameAutosaveTimer;
	String mPreviousName;

    BroadcastReceiver mDevicesReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
	    // создаем BroadcastReceiver
		mDevicesReceiver = new BroadcastReceiver() 
		{
			public void onReceive(Context context, Intent intent) 
			{
				updateDevicesFromIntent(intent);
			}
	    };
	    // создаем фильтр для BroadcastReceiver
	    IntentFilter intFilt = new IntentFilter(RemoteDialerService.DEVICES_BROADCAST);
	    // регистрируем BroadcastReceiver
	    registerReceiver(mDevicesReceiver, intFilt);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
    	mSettings = getSharedPreferences("RDialerPrefs", MODE_PRIVATE);
    	mPreviousName = mSettings.getString("device_name", RemoteDialerService.DEFAULT_DEVICE_NAME);
    	edtName.setText(mPreviousName);
    	cbAutostart.setChecked(mSettings.getBoolean("autostart", true));
    	mDeviceNameAutosaveTimer = null;
    	updateDevicesFromIntent(getIntent());
	    //ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, allStreets.split("\\|"));
	}	
	
    @Override
    protected void onDestroy() {
      super.onDestroy();
      // выключаем BroadcastReceiver
      unregisterReceiver(mDevicesReceiver);
    }

    private void updateDevicesFromIntent(Intent intent)
    {
		ArrayList<RemoteDevice> devices = intent.getParcelableArrayListExtra(RemoteDialerService.DEVICES_EXTRA);
		for (int i = 0; i < devices.size(); ++i)
		{
			RemoteDevice device = devices.get(i);
			if (device.mType == RemoteDevice.DEVICE_TYPE_THIS)
			{
				device.mModel = getResources().getString(R.string.this_device);
				devices.set(i, device);
			}
		}
        DeviceAdapter devicesAdapter = new DeviceAdapter(this, R.layout.device_list_item, devices);
        atvDefaultDevice.setAdapter(devicesAdapter);
	    atvDefaultDevice.setThreshold(1);
        atvDefaultDevice.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                	atvDefaultDevice.showDropDown();
            }
        });
    }
    
	@TextChange(R.id.edtName)
	void onDeviceNameChange(TextView tv, CharSequence text) 
	{
		System.out.println(mPreviousName + " " + text);
		if (!text.toString().equals(mPreviousName))
		{
			System.out.println("restarting");
			mPreviousName = text.toString();
    		Editor e = mSettings.edit();
    		e.putString("device_name", text.toString());
    		e.commit();
			if (mDeviceNameAutosaveTimer != null)
				mDeviceNameAutosaveTimer.cancel();
			mDeviceNameAutosaveTimer = new Timer();
			final Intent restartIntent = new Intent(this, RemoteDialerService_.class);
			mDeviceNameAutosaveTimer.schedule(new TimerTask() {
	            @Override
	            public void run() {
	            	//runOnUiThread(new Runnable() {public void run(){
	            		startService(restartIntent
	        			.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_RESTART));	
	            	//}});
	            }
	    	}, 10000);
		}
	}
	
}
