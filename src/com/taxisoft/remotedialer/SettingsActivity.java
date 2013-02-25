package com.taxisoft.remotedialer;

import java.util.Timer;
import java.util.TimerTask;

import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.TextChange;
import com.googlecode.androidannotations.annotations.ViewById;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
	
	SharedPreferences mSettings;
	Timer mDeviceNameAutosaveTimer;
	String mPreviousName;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
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
