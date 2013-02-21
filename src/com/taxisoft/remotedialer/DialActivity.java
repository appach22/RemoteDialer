package com.taxisoft.remotedialer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.googlecode.androidannotations.annotations.Click;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.ViewById;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

@EActivity(R.layout.activity_dial)
public class DialActivity extends Activity
{

	private final static int CMD_CODE	= 1;

    @ViewById
    Button btnStart;
    @ViewById
    Button btnStop;
    @ViewById
    EditText edtNumber;
    @ViewById
    Button btnDial;
    @ViewById
    Button btnCancel;
    @ViewById
    Spinner spnDevices;
    
    BroadcastReceiver m_devicesReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_dial);
	    // создаем BroadcastReceiver
		m_devicesReceiver = new BroadcastReceiver() 
		{
			public void onReceive(Context context, Intent intent) 
			{
				updateDevicesFromIntent(intent);
			}
	    };
	    // создаем фильтр для BroadcastReceiver
	    IntentFilter intFilt = new IntentFilter(RemoteDialerService.DEVICES_BROADCAST);
	    // регистрируем BroadcastReceiver
	    registerReceiver(m_devicesReceiver, intFilt);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
    	System.out.println("onStart");
    	PendingIntent pi = createPendingResult(RemoteDialerService.CMD_GET_DEVICES, new Intent(), 0);
    	// Получаем список уже найденных устройств
		startService(new Intent(this, RemoteDialerService.class)
		.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_GET_DEVICES)
		.putExtra(RemoteDialerService.CMD_PENDING_EXTRA, pi)
		);	
    	
    	Uri data = getIntent().getData();
    	if (data == null)
    		return;
    	System.out.println(data.getSchemeSpecificPart());
    	edtNumber.setText(data.getSchemeSpecificPart().trim());		
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
	            startActivity(new Intent(this, SettingsActivity_.class));
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Click(R.id.btnStart)
	public void StartService()
	{
		startService(new Intent(this, RemoteDialerService.class)
			.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_START));	
	}

	@Click(R.id.btnStop)
	public void StopService()
	{
		stopService(new Intent(this, RemoteDialerService.class));	
	}

	@Click(R.id.btnDial)
	public void DialNumber()
	{
		sendRequest((RemoteDevice)spnDevices.getSelectedItem(), "DialNumber " + edtNumber.getText());
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
    }	

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (resultCode == RemoteDialerService.CMD_RES_SUCCESS)
    	{
    		if (requestCode == RemoteDialerService.CMD_GET_DEVICES)
    		{
    			updateDevicesFromIntent(data);
    		}
    	}
    }
    
    @Override
    protected void onDestroy() {
      super.onDestroy();
      // выключаем BroadcastReceiver
      unregisterReceiver(m_devicesReceiver);
    }

    private void updateDevicesFromIntent(Intent intent)
    {
		ArrayList<RemoteDevice> devices = intent.getParcelableArrayListExtra(RemoteDialerService.DEVICES_EXTRA);
        ArrayAdapter<RemoteDevice> devicesAdapter = new ArrayAdapter<RemoteDevice>(getApplicationContext(),android.R.layout.simple_spinner_item,  devices);
        spnDevices.setAdapter(devicesAdapter);
    }
    
    private String sendRequest(RemoteDevice device, String request)
    {
        Socket clientSocket;
        String reply = "";
		try
		{
			clientSocket = new Socket(device.m_host, device.m_port);
	        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
	        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	        outToServer.writeBytes(request + "\n");
	        reply = inFromServer.readLine();
	        clientSocket.close();
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
    	return reply;
    }

    
}
