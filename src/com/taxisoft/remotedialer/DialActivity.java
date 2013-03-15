package com.taxisoft.remotedialer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.TextChange;
import com.googlecode.androidannotations.annotations.UiThread;
import com.googlecode.androidannotations.annotations.ViewById;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

@EActivity(R.layout.activity_dial)
public class DialActivity extends Activity
{

	//private final static int CMD_CODE	= 1;

    @ViewById
    EditText edtNumber;
    @ViewById
    Button btnDial;
    @ViewById
    Button btnCancel;
    @ViewById
    Spinner spnDevices;
    
    BroadcastReceiver mDevicesReceiver;
    
    ArrayList<RemoteDevice> mDevices;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dial);
		mDevices = new ArrayList<RemoteDevice>();
	    // создаем BroadcastReceiver
		mDevicesReceiver = new BroadcastReceiver() 
		{
			public void onReceive(Context context, Intent intent) 
			{
				updateDevicesFromIntent(intent);
			}
	    };
	    // создаем фильтр для BroadcastReceiver
	    IntentFilter intFilt = new IntentFilter(RemoteDialerDevices.DEVICES_BROADCAST);
	    // регистрируем BroadcastReceiver
	    registerReceiver(mDevicesReceiver, intFilt);
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		System.out.println("onStart");
    	PendingIntent pi = createPendingResult(RemoteDialerService.CMD_GET_FOUND_DEVICES, new Intent(), 0);
    	// Получаем список уже найденных устройств
		startService(new Intent(this, RemoteDialerService_.class)
		.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_GET_FOUND_DEVICES)
		.putExtra(RemoteDialerService.CMD_PENDING_EXTRA, pi)
		);	
    	
    	Uri data = getIntent().getData();
    	if (data == null)
    		return;
    	System.out.println(data.getSchemeSpecificPart());
    	edtNumber.setText(/*URLDecoder.decode(*/data.getSchemeSpecificPart().trim());		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_dial, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.menu_settings:
	            startActivity(new Intent(this, SettingsActivity_.class).putExtra(RemoteDialerDevices.DEVICES_EXTRA, mDevices));
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	private void dialLocally(String number)
	{
    	PendingIntent pi = createPendingResult(RemoteDialerService.CMD_DIAL_NUMBER, new Intent(), 0);
    	// Получаем список уже найденных устройств
		startService(new Intent(this, RemoteDialerService_.class)
		.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_DIAL_NUMBER)
		.putExtra(RemoteDialerService.CMD_PARAM_EXTRA, number)
		.putExtra(RemoteDialerService.CMD_PENDING_EXTRA, pi)
		);	
	}
	
	@Click(R.id.btnDial)
	public void DialNumber()
	{
		RemoteDevice device = (RemoteDevice)spnDevices.getSelectedItem();
		if (device.mType == RemoteDevice.DEVICE_TYPE_THIS)
			dialLocally(edtNumber.getText().toString());
		else
			// TODO: увести в фон
			sendRequest(device, "DialNumber " + edtNumber.getText());
	}
	
	@Click(R.id.btnCancel)
	public void ExitDialer()
	{
		finish();
	}
	
    @Override
    protected void onResume() 
    {
    	super.onResume();
    	System.out.println("onResume");
//    	PendingIntent pi = createPendingResult(RemoteDialerService.CMD_GET_FOUND_DEVICES, new Intent(), 0);
//    	// Получаем список уже найденных устройств
//		startService(new Intent(this, RemoteDialerService_.class)
//		.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_GET_FOUND_DEVICES)
//		.putExtra(RemoteDialerService.CMD_PENDING_EXTRA, pi)
//		);	
    }	

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
    	super.onActivityResult(requestCode, resultCode, data);
    	//System.out.println("onActivityResult(): " + requestCode + " " + resultCode);
    	if (resultCode == RemoteDialerService.CMD_RES_SUCCESS)
    	{
    		if (requestCode == RemoteDialerService.CMD_GET_DEVICES ||
    			requestCode == RemoteDialerService.CMD_GET_FOUND_DEVICES)
    		{
    			// Получаем список доступных устройств
    			updateDevicesFromIntent(data);
    	        // Делаем устройство по умолчанию активным в списке
    			setDefaultDeviceActive();
    		}
    	}
    	if (resultCode == RemoteDialerService.CMD_RES_FAILURE)
    	{
    		if (requestCode == RemoteDialerService.CMD_GET_DEVICES || 
    			requestCode == RemoteDialerService.CMD_GET_FOUND_DEVICES)
    			Toast.makeText(this, "Error getting device list", Toast.LENGTH_LONG).show();
    		else if (requestCode == RemoteDialerService.CMD_DIAL_NUMBER)
    			Toast.makeText(this, "Error dialing number", Toast.LENGTH_LONG).show();
    	}
    }
    
    @Override
    protected void onDestroy() {
      super.onDestroy();
      // выключаем BroadcastReceiver
      unregisterReceiver(mDevicesReceiver);
    }

    private void setDefaultDeviceActive()
    {
        SharedPreferences settings = getSharedPreferences("RDialerPrefs", MODE_PRIVATE);
        RemoteDevice device = new RemoteDevice().
    			InitLocal(settings.getString("default_device_name", ""), settings.getString("default_device_uid", ""));
		int pos = mDevices.indexOf(device);
		if (pos != -1)
			spnDevices.setSelection(pos);
    }
    
    private void updateDevicesFromIntent(Intent intent)
    {
		mDevices = intent.getParcelableArrayListExtra(RemoteDialerDevices.DEVICES_EXTRA);
    	//System.out.println("updateDevicesFromIntent(): " + mDevices);
		for (int i = 0; i < mDevices.size(); ++i)
		{
			RemoteDevice device = mDevices.get(i);
			if (device.mType == RemoteDevice.DEVICE_TYPE_THIS)
			{
				device.mModel = getResources().getString(R.string.this_device);
				mDevices.set(i, device);
			}
		}
        DeviceAdapter devicesAdapter = new DeviceAdapter(this, R.layout.device_list_item, mDevices);
        spnDevices.setAdapter(devicesAdapter);
        spnDevices.setPrompt(getResources().getString(R.string.select_device));
    }
    
    @UiThread
    protected void showProgress(String message, ProgressDialog dialog[])
    {
    	dialog[0] = (ProgressDialog.show(this, "", message, true));
    }
    
    @UiThread
    protected void showToast(String message, int duration)
    {
    	Toast.makeText(this, message, duration).show();    
    }
    
    // Пока request только один: DialNumber, можем себе позволить показывать прогресс прямо в sendRequest
    @Background
    protected void sendRequest(RemoteDevice device, String request)
    {
        Socket clientSocket;
        String reply = "";
        ProgressDialog dialog[] = new ProgressDialog[1];
        showProgress(getResources().getString(R.string.dialing_number), dialog);
		try
		{
			clientSocket = new Socket(device.mHost, device.mPort);
	        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
	        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	        outToServer.writeBytes(request + "\n");
	        reply = inFromServer.readLine();
	        clientSocket.close();
			dialog[0].dismiss();
	    	if (reply.equalsIgnoreCase("Accepted"))
				showToast(getResources().getString(R.string.dial_success), Toast.LENGTH_SHORT);
	    	else
				showToast(getResources().getString(R.string.dial_error), Toast.LENGTH_LONG);
		} catch (UnknownHostException e)
		{
			dialog[0].dismiss();
			e.printStackTrace();
			//Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
			showToast(getResources().getString(R.string.device_connection_error), Toast.LENGTH_LONG);
		} catch (IOException e)
		{
			dialog[0].dismiss();
			e.printStackTrace();
			//Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
			showToast(getResources().getString(R.string.device_connection_error), Toast.LENGTH_LONG);
		}
    }

	@TextChange(R.id.edtNumber)
	void onNumberChange(TextView tv, CharSequence text) 
	{
		//System.out.println(PhoneNumberUtils.formatNumber(edtNumber.getText().toString()));
	}
    
}
