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
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

@EActivity(R.layout.activity_settings)
public class SettingsActivity extends Activity implements OnEditorActionListener
{
	@ViewById
	EditText edtName;
	@ViewById
	CheckBox cbAutostart;
	@ViewById
	AutoCompleteTextView atvDefaultDevice;
	
	SharedPreferences mSettings;
	Timer mDeviceNameAutosaveTimer;
	Timer mDefaultDeviceAutosaveTimer;
	String mPreviousName;
	RemoteDevice mSavedDefaultDevice;
	RemoteDevice mSelectedDefaultDevice;
	ArrayList<RemoteDevice> mDevices;
	boolean mIsFirstTime = true;

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
    	updateDevicesFromIntent(getIntent());
    	mIsFirstTime = true;
    	mSettings = getSharedPreferences("RDialerPrefs", MODE_PRIVATE);
    	mPreviousName = mSettings.getString("device_name", RemoteDialerService.DEFAULT_DEVICE_NAME);
    	edtName.setText(mPreviousName);
    	cbAutostart.setChecked(mSettings.getBoolean("autostart", true));
    	mSavedDefaultDevice = new RemoteDevice().
    			InitLocal(mSettings.getString("default_device_name", ""), mSettings.getString("default_device_uid", ""));
    	atvDefaultDevice.setText(mSavedDefaultDevice.mName);
    	mDeviceNameAutosaveTimer = null;
	    edtName.setOnEditorActionListener(this);
	    atvDefaultDevice.setOnEditorActionListener(this);
	}	
	
    @Override
    public boolean onEditorAction(TextView v, int keyCode, KeyEvent event) 
    {
    	// Почему-то на ENTER возвращает KEYCODE_CALL
//    	System.out.println(keyCode);
//        if (/*event != null && event.getAction() == KeyEvent.ACTION_DOWN && */keyCode == KeyEvent.KEYCODE_ENTER)
//        {           
//        	System.out.println("hide");
//        	// hide virtual keyboard
//        	InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
//            imm.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
//            return true;
//        }
        return false;
    }
    
    @Override
    protected void onDestroy() {
      super.onDestroy();
      // выключаем BroadcastReceiver
      unregisterReceiver(mDevicesReceiver);
    }

    @SuppressWarnings("unchecked")
	private void updateDevicesFromIntent(Intent intent)
    {
		mDevices = intent.getParcelableArrayListExtra(RemoteDialerService.DEVICES_EXTRA);
		for (int i = 0; i < mDevices.size(); ++i)
		{
			RemoteDevice device = mDevices.get(i);
			if (device.mType == RemoteDevice.DEVICE_TYPE_THIS)
			{
				device.mModel = getResources().getString(R.string.this_device);
				mDevices.set(i, device);
			}
		}
        DeviceAdapter devicesAdapter = new DeviceAdapter(this, R.layout.device_list_item, (ArrayList<RemoteDevice>)mDevices.clone());
        atvDefaultDevice.setAdapter(devicesAdapter);
	    atvDefaultDevice.setThreshold(1);
        atvDefaultDevice.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                	atvDefaultDevice.showDropDown();
            }
        });
//        atvDefaultDevice.setOnItemClickListener(new OnItemClickListener() { 
//            @Override
//            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
//            	System.out.println("Item click " + position);
//            	RemoteDevice device = (RemoteDevice) adapterView.getItemAtPosition(position);
//        		Editor e = mSettings.edit();
//        		e.putString("default_device_name", device.mName);
//        		e.putString("default_device_uid", device.mUid);
//        		e.commit();
//        		System.out.println("Saved def: " + device.mName + " " + device.mUid);
//            }
//        });
        //atvDefaultDevice.setOnEditorActionListener(l)
    }
    
	@TextChange(R.id.edtName)
	void onDeviceNameChange(TextView tv, CharSequence text) 
	{
		System.out.println(mPreviousName + " " + text);
		if (!text.toString().equals(mPreviousName))
		{
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
	
	@TextChange(R.id.atvDefaultDevice)
	void onDefaultDeviceNameChange(TextView tv, CharSequence text) 
	{
		if (mIsFirstTime)
		{
			mIsFirstTime = false;
			return;
		}
		//System.out.println("Text changed: " + text);
    	//System.out.println(mDevices);
		RemoteDevice device;
    	if (text.toString().equals(""))
    	{
    		//System.out.println("empty");
    		// Чтобы при пустом тексте отображались все устройства, надо наследоваться от atv
    		atvDefaultDevice.showDropDown();
	    	device = new RemoteDevice().InitLocal("", "");
    	}
    	else
    	{
	    	device = new RemoteDevice().InitLocal(text.toString(), "");
	    	// Пробуем найти по имени указанное устройство среди уже существующих
	    	if (mDevices != null)
	    	{
	    		int pos = mDevices.indexOf(device);
	    		if (pos != -1)
	    			device = mDevices.get(pos);
	    	}
    	}
		Editor e = mSettings.edit();
		e.putString("default_device_name", device.mName);
		e.putString("default_device_uid", device.mUid);
		e.commit();
		//System.out.println("Saved def: " + device.mName + " " + device.mUid);
	}
}
