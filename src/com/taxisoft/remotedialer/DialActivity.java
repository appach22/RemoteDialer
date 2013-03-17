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
import android.os.Handler;
import android.os.Message;
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
    
    private BroadcastReceiver mDevicesReceiver;
    
    private RemoteDialerDevices mDevices;
    
    private boolean mWasDefault = false;
    private RemoteDevice mManuallySelectedDevice = null;
    private ProgressDialog mDialingProgress = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dial);
		//mDevices = new RemoteDialerDevices(this);
		mManuallySelectedDevice = null;
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
	            startActivity(new Intent(this, SettingsActivity_.class).putParcelableArrayListExtra(RemoteDialerDevices.DEVICES_EXTRA, mDevices));
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
    			int defaultPos = mDevices.getDefaultDeviceIndex();
    			if (defaultPos != -1)
    				spnDevices.setSelection(defaultPos);
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

    private void updateDevicesFromIntent(Intent intent)
    {
    	// Устанавливаем новый список в GUI
    	
    	mDevices = intent.getParcelableExtra(RemoteDialerDevices.DEVICES_EXTRA);
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
        
        // Выбираем текущий элемент (hurja urakka!)       
        
        int defaultPos = mDevices.getDefaultDeviceIndex();
        // Если появилось в списке устройство по умолчанию
        if (defaultPos != -1)
        {
        	// ...(а раньше не было)
        	if (!mWasDefault)
        	{
        		// Выбираем устройство по умолчанию
        		spnDevices.setSelection(defaultPos);
        		mWasDefault = true;
        		return;
        	}
        }
        else
    		mWasDefault = false;
        
        // Если с момента запуска программы пользователь вручную выбирал устройство из списка...
        if (mManuallySelectedDevice != null)
        {
        	int manualPos = mDevices.indexOf(mManuallySelectedDevice);
            // ...и данное устройство сохранилось в списке
        	if (manualPos != -1)
        	{
        		// Оставляем выбранное пользователем устройство
        		spnDevices.setSelection(manualPos);
        		return;
        	}
        }
        
        // Если пользователь либо ни разу не выбирал устройство, 
        // либо выбранное им устройство исчезло 
        // Если в списке присутствует устройство по умолчанию
        if (defaultPos != -1)
    	{
    		// Выбираем устройство по умолчанию
    		spnDevices.setSelection(defaultPos);
    		return;
    	}
        // Если в списке устр-ва по умолчанию нет - пробуем установить локальное устройство
        else 
        {
        	int localPos = mDevices.getLocalDeviceIndex();
        	// Если есть локальное устройство
        	if (localPos != -1)
        	{
        		// Выбираем локальное
        		spnDevices.setSelection(localPos);
        		return;
        	}
        	// Если нет и локального устройства
        	else
        		// Просто выбираем первое из списка
        		spnDevices.setSelection(0);
        }
    }

    
    //======================== Dialing GUI stuff ======================================
    
    private Handler mHandler = new Handler() {
        @Override
            public void handleMessage(Message msg) {
        	if (mDialingProgress != null)
        	{
        		mDialingProgress.dismiss();
        		mDialingProgress = null;
        	}
        }
    };
    
    @UiThread
    protected void showProgress(String message)
    {
    	mDialingProgress = ProgressDialog.show(this, "", message, true);
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
        showProgress(getResources().getString(R.string.dialing_number));
		try
		{
			clientSocket = new Socket(device.mHost, device.mPort);
	        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
	        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	        outToServer.writeBytes(request + "\n");
	        reply = inFromServer.readLine();
	        clientSocket.close();
	        mHandler.sendEmptyMessage(0);
	    	if (reply.equalsIgnoreCase("Accepted"))
				showToast(getResources().getString(R.string.dial_success), Toast.LENGTH_SHORT);
	    	else
				showToast(getResources().getString(R.string.dial_error), Toast.LENGTH_LONG);
		} catch (UnknownHostException e)
		{
			mHandler.sendEmptyMessage(0);
			e.printStackTrace();
			//Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
			showToast(getResources().getString(R.string.device_connection_error), Toast.LENGTH_LONG);
		} catch (IOException e)
		{
			mHandler.sendEmptyMessage(0);
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
