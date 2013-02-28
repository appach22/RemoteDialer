package com.taxisoft.remotedialer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EService;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.IBinder;
import android.telephony.TelephonyManager;

@EService
public class RemoteDialerService extends Service  
{

	public final static String DEFAULT_DEVICE_NAME = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
	//private final static String LOG_TAG = "RemoteDialerService";
    private final static String RDIALER_SERVICE_TYPE_WITH_DOMAIN = "_rdialer._tcp.local.";
    private final static String RDIALER_SERVICE_TYPE = "_rdialer._tcp";
    //private final static String RDIALER_SERVICE_DESCRIPTION = "Remote Dialer service";
	protected final static String DEVICES_EXTRA = "devices";
	protected final static String DEVICES_BROADCAST = "com.taxisoft.remotedialer.devices";
	protected final static String CMD_EXTRA = "command";
	protected final static String CMD_PARAM_EXTRA = "command parameter";
	protected final static String CMD_PENDING_EXTRA = "pending";
	
	protected final static int CMD_NONE 		= 0;
	protected final static int CMD_START		= 1;
	protected final static int CMD_RESTART		= 2;
	protected final static int CMD_GET_DEVICES 	= 3;
	protected final static int CMD_DIAL_NUMBER	= 4;

	protected final static int CMD_RES_SUCCESS		= 0;
	protected final static int CMD_RES_NO_SUCH_CMD	= 1;
	protected final static int CMD_RES_FAILURE		= 2;

	private final static int STATE_STOPPED 	= 1;
	private final static int STATE_STARTING = 2;
	private final static int STATE_RUNNING 	= 4;
	private final static int STATE_STOPPING = 8;

    private JmDNS mJmDNSService = null;
    private JmDNS mJmDNSListener = null;
    private MulticastLock mLock;
    private ServiceListener mListener = null;
    private ServiceInfo mServiceInfo;
	private String mDeviceName;
	private String mDefaultDeviceName;
	private ArrayList<RemoteDevice> mDevices;
	private static int mServiceState = STATE_STOPPED;
	private ConnectionStateReceiver mConnStateReceiver;	

    class CommandReceiveTask extends AsyncTask<ServerSocket, Void, Void>
    {

    	@Override
    	protected Void doInBackground(ServerSocket... params)
    	{
    		
    		try
    		{
    			String clientRequest;
    	        String serverReply = "Unknown request\n";
    			ServerSocket welcomeSocket = params[0];
    			while(true)
    			{
    			    Socket connectionSocket = welcomeSocket.accept();
    			    BufferedReader inFromClient =
    			       new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
    			    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
    			    clientRequest = inFromClient.readLine();
    			    System.out.println("Received: " + clientRequest);
    			    if (clientRequest.equalsIgnoreCase("GetDeviceName"))
    			    	serverReply = mDeviceName + '\n';
    			    else if (clientRequest.contains("DialNumber"))
    			    {
    			    	dialNumber(clientRequest.split(" ")[1]);
    			    	serverReply = "Accepted\n";
    			    }
    			    outToClient.writeBytes(serverReply);
    			}
    			
    		} catch (IOException e)
    		{
    			e.printStackTrace();
    		}
    		return null;
    	}
    }
    
    class ConnectionStateReceiver extends BroadcastReceiver
    {
		@Override
		public void onReceive(Context context, Intent intent)
		{
		    final String action = intent.getAction();
//		    if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION))
//		    {
//		        if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
//		            System.out.println("Wifi connected");
//		            if (isWifiReady())
//		            	startRemoteDialerService();
//		        } else {
//		            System.out.println("Wifi disconnected");
//		            stopRemoteDialerService();
//		        }
//		    }
		    if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION))
		    {
		    	NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO); 
		    	if (info != null)
		    	{
		    		System.out.println("Network state is " + info.getState());
		    		if (info.getState() == NetworkInfo.State.CONNECTED)
		    		{
		    			//stopRemoteDialerService();
		    			startRemoteDialerService();
		    			return;
		    		}
		    	}
		    	else
		    		System.out.println("Network state error!");
		    	stopRemoteDialerService();
		    }
		}
    }
	
	@Override
	public IBinder onBind(Intent arg0)
	{
		return null;
	}

	private boolean isWifiNetworkReady()
	{
        //WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        //WifiInfo info = wifi.getN
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
        if (info != null)
        {
        	System.out.println("isWifiNetworkReady(): " + info.getType() + " " + info.getState());
        	// FIXME: иногда возвращает TYPE_MOBILE !!!!!!!1
        	if (info.getType() == ConnectivityManager.TYPE_WIFI && info.getState() == NetworkInfo.State.CONNECTED)
        		return true;
        }
        else
        {
        	System.out.println("no info");
        	return false;
        }
        return false;
	}
	
	private boolean isPhoneAvailable()
	{
		if (((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getPhoneType()
			    == TelephonyManager.PHONE_TYPE_NONE)		
			return false;
		else
			return true;
	}
	
	private void reportNewDevices()
	{
		Intent clientIntent = new Intent(DEVICES_BROADCAST);
    	clientIntent.putExtra(DEVICES_EXTRA, mDevices);
    	sendBroadcast(clientIntent);	
	}
	
	private int waitForServiceState(int state)
	{
		try
		{
			while ((mServiceState & state) == 0)
				Thread.sleep(100);
		} catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return 0;
		}
		return mServiceState;
	}
	
	private void addDevice(ServiceInfo info)
	{
    	RemoteDevice device = new RemoteDevice().Init(info);
    	// Если список уже содержит это устройство - обновляем его в списке (на случай, если порт поменялся)
    	// FIXME: почему-то НЕ РАБОТАЕТ!!!
    	if (mDevices.contains(device))
    		mDevices.remove(device);
		mDevices.add(device);
		reportNewDevices();
	}
	
	private void removeDevice(ServiceInfo info)
	{
    	RemoteDevice device = new RemoteDevice().Init(info);
    	if (mDevices.contains(device))
    	{
    		mDevices.remove(device);
    		reportNewDevices();
    	}
	}
	
	private void stopRemoteDialerService()
	{
		System.out.println("stop enter " + mServiceState);
		if (mServiceState == STATE_STOPPING || mServiceState == STATE_STOPPED)
			return;
		if (mServiceState == STATE_STARTING)
			if (waitForServiceState(STATE_RUNNING | STATE_STOPPED) == STATE_STOPPED)
				return;
		
		mServiceState = STATE_STOPPING;
		// Удаляем из списка все устройства кроме локального (если таковое есть)
		RemoteDevice thisDevice = null;
		for (int i = 0; i < mDevices.size(); ++i)
			if (mDevices.get(i).mType == RemoteDevice.DEVICE_TYPE_THIS)
			{
				thisDevice = mDevices.get(i);
				break;
			}
		mDevices.clear();
		if (thisDevice != null)
			mDevices.add(thisDevice);
		reportNewDevices();
		try
		{
			if (mListener != null && mJmDNSListener != null)
			{
				mJmDNSListener.removeServiceListener(mServiceInfo.getType(), mListener);
				mJmDNSListener.close();
			}
			if (mJmDNSService != null)
			{
				mJmDNSService.unregisterAllServices();
				mJmDNSService.close();
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		if (mLock != null)
			mLock.release();
		mServiceState = STATE_STOPPED;
        System.out.println("Service stopped");
		System.out.println("stop exit " + mServiceState);
	}
	
	private void startRemoteDialerService()
	{
		System.out.println("start enter " + mServiceState);
		if (mServiceState == STATE_STARTING || mServiceState == STATE_RUNNING)
			return;
		if (mServiceState == STATE_STOPPING)
			waitForServiceState(STATE_STOPPED);
		
		mServiceState = STATE_STARTING;
		
		// Получаем из настроек имя устройства
	    mDeviceName = getSharedPreferences("RDialerPrefs", MODE_PRIVATE)
	    		.getString("device_name", DEFAULT_DEVICE_NAME);
	    
		// Получаем из настроек имя устройства по умолчанию
	    mDefaultDeviceName = getSharedPreferences("RDialerPrefs", MODE_PRIVATE)
	    		.getString("default_device_name", DEFAULT_DEVICE_NAME);
	    
		// Если сотовая связь доступна, добавляем локальное устройство в список
		if (isPhoneAvailable())
		{
			System.out.println("start phone");
			// --------- FIXME: убрать. Это временный хак пока не поддержан device uid
			for (int i = 0; i < mDevices.size(); ++i)
				if (mDevices.get(i).mType == RemoteDevice.DEVICE_TYPE_THIS)
				{
					mDevices.remove(i);
					break;
				}
			// -----------------------------------------------------------------------
			RemoteDevice device = new RemoteDevice().InitLocal(mDeviceName);	
        	if (!mDevices.contains(device))
        	{
    			System.out.println("start self");
        		mDevices.add(device);
        		reportNewDevices();
        	}
		}
		
		// Проверяем доступность сети Wi-Fi
		if (isWifiNetworkReady())
		{		
			System.out.println("start wifi");
			// Если сеть доступна - будем слушать сервисы RemoteDialer в локальной сети
	        WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
	        mLock = wifi.createMulticastLock("mylockthereturn");
	        mLock.setReferenceCounted(true);
	        mLock.acquire();
	        try {
	            mJmDNSService = JmDNS.create("Service");
	            mJmDNSListener = JmDNS.create("Listener");
	            
	    		// Если сотовая связь доступна, позиционируем себя как сервис
	            ServerSocket socket = new ServerSocket(0);
	            mServiceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE_WITH_DOMAIN, mDeviceName, socket.getLocalPort(), DEFAULT_DEVICE_NAME);
	    		if (isPhoneAvailable())
	    		{
		            System.out.println("Removing old service...");
		            mJmDNSService.registerService(mServiceInfo);
		            mJmDNSService.unregisterService(mServiceInfo);
		            try
					{
						Thread.sleep(5000);
					} catch (InterruptedException e)
					{
						e.printStackTrace();
					}
		            System.out.println("Registering new service...");
		            mJmDNSService.registerService(mServiceInfo);
		            // Запускаем фоновую обработку запросов
		            processRequest(socket);
	    		}
	    		
	            // TODO: делать list, дабы определить уже зарегистрированные сервисы
	            ServiceInfo services[] = mJmDNSService.list(mServiceInfo.getType(), 6000);
	            for (int i = 0; i < services.length; ++i)
	            	addDevice(services[i]);
	            mJmDNSListener.addServiceListener(mServiceInfo.getType(), mListener = new ServiceListener() {
	
	                @Override
	                public void serviceResolved(ServiceEvent ev) {
	                	System.out.println("Service resolved: " + ev.getInfo().getName() + " port:" + ev.getInfo().getPort());
	                	addDevice(ev.getInfo());
	                }
	
	                @Override
	                public void serviceRemoved(ServiceEvent ev) {
	                	System.out.println("Service removed: " + ev.getName());
	                	removeDevice(ev.getInfo());
	                }
	
	                @Override
	                public void serviceAdded(ServiceEvent event) {
	                	System.out.println("Service added: " + event.getInfo().getName() + " port:" + event.getInfo().getPort());
	                    // Required to force serviceResolved to be called again (after the first search)
	                	mJmDNSListener.requestServiceInfo(event.getType(), event.getName(), 1);
	                }
	            });
	            System.out.println("Service started. Name=" + mDeviceName);
	            mServiceState = STATE_RUNNING;
	        } catch (IOException e) {
				mServiceState = STATE_STOPPED;
	            e.printStackTrace();
	            return;
	        }
		}
		else //if (!isPhoneAvailable())   
			// Если недоступен Wi-Fi - Давай, до свидания!
			mServiceState = STATE_STOPPED;
		System.out.println("start exit " + mServiceState);
	}
	
    private boolean dialNumber(String number)
    {
    	if (number == null || number.length() == 0)
    		return false; 
    	Intent callIntent = new Intent(Intent.ACTION_CALL);
    	callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	callIntent.setData(Uri.parse("tel:" + number));
    	startActivity(callIntent);
    	return true;
    }

	public void onCreate() 
	{
	    super.onCreate();
	    mDevices = new ArrayList<RemoteDevice>();
	    mServiceState = STATE_STOPPED;
	    // Регистрируем слушателя состояния сети wifi
	    mConnStateReceiver = new ConnectionStateReceiver();
	    IntentFilter intentFilter = new IntentFilter();
	    //intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
	    intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
	    registerReceiver(mConnStateReceiver, intentFilter);
	    
	    System.out.println("onCreate()");
	}	
	
	@Background
	protected void processRequest(ServerSocket socket)
	{
		try
		{
			String clientRequest;
	        String serverReply = "Unknown request\n";
			while(true)
			{
			    Socket connectionSocket = socket.accept();
			    BufferedReader inFromClient =
			       new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			    clientRequest = inFromClient.readLine();
			    System.out.println("Received: " + clientRequest);
			    if (clientRequest.equalsIgnoreCase("GetDeviceName"))
			    	serverReply = mDeviceName + '\n';
			    else if (clientRequest.contains("DialNumber"))
			    {
			    	dialNumber(clientRequest.split(" ")[1]);
			    	serverReply = "Accepted\n";
			    }
			    outToClient.writeBytes(serverReply);
			}
			
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@Background
	protected void processCommand(Intent intent)
	{
		try
		{
			PendingIntent pi = intent.getParcelableExtra(CMD_PENDING_EXTRA);
			switch(intent.getIntExtra(CMD_EXTRA, CMD_NONE))
			{
			case CMD_START:
		    	startRemoteDialerService();
		    	break;
			case CMD_RESTART:
		    	stopRemoteDialerService();
		    	startRemoteDialerService();
		    	break;
			case CMD_GET_DEVICES:
				if (pi != null)
				{
			    	startRemoteDialerService();
					Intent clientIntent = new Intent().putExtra(DEVICES_EXTRA, mDevices);
					pi.send(this, CMD_RES_SUCCESS, clientIntent);
				}
				break;
			case CMD_DIAL_NUMBER:
				if (pi != null)
				{
					if (dialNumber(intent.getStringExtra(CMD_PARAM_EXTRA)))
						pi.send(CMD_RES_SUCCESS);
					else
						pi.send(CMD_RES_FAILURE);
				}
				break;
			default:
				if (pi != null)
					pi.send(CMD_RES_NO_SUCH_CMD);
			}
		} catch (CanceledException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		System.out.println("onStartCommand");
		if (intent == null)
			return START_STICKY;
		processCommand(intent);
		return START_STICKY;
	}

	public void onDestroy() 
	{
		stopRemoteDialerService();
        unregisterReceiver(mConnStateReceiver);
	}

}
