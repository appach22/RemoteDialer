package com.taxisoft.remotedialer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.TelephonyManager;

public class RemoteDialerService extends Service  
{

	private final static String DEFAULT_DEVICE_NAME = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
	//private final static String LOG_TAG = "RemoteDialerService";
    private final static String RDIALER_SERVICE_TYPE = "_rdialer._tcp.local.";
    private final static String RDIALER_SERVICE_DESCRIPTION = "Remote Dialer service";
	protected final static String DEVICES_EXTRA = "devices";
	protected final static String DEVICES_BROADCAST = "com.taxisoft.remotedialer.devices";
	protected final static String CMD_EXTRA = "command";
	protected final static String CMD_PENDING_EXTRA = "pending";
	
	protected final static int CMD_NONE 		= 0;
	protected final static int CMD_START		= 1;
	protected final static int CMD_GET_DEVICES 	= 2;

	protected final static int CMD_RES_SUCCESS		= 0;
	protected final static int CMD_RES_NO_SUCH_CMD	= 1;
	protected final static int CMD_RES_FAILURE		= 2;

    private JmDNS m_jmdns = null;
    private MulticastLock m_lock;
    private ServiceListener m_listener = null;
    private ServiceInfo m_serviceInfo;
	private String m_deviceName;
	private ArrayList<RemoteDevice> m_devices;
	private static boolean m_isStarted = false;
	private ConnectionStateReceiver m_connStateReceiver;	

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
    			    	serverReply = m_deviceName + '\n';
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
    	clientIntent.putExtra(DEVICES_EXTRA, m_devices);
    	sendBroadcast(clientIntent);	
	}
	
	private void stopRemoteDialerService()
	{
		if (!m_isStarted)
			return;
		
		m_devices.clear();
		reportNewDevices();
		if (m_listener != null)
			m_jmdns.removeServiceListener(RDIALER_SERVICE_TYPE, m_listener);
		m_jmdns.unregisterAllServices();
		m_lock.release();
		m_isStarted = false;
        System.out.println("Service stopped");
	}
	
	private void startRemoteDialerService()
	{
		if (m_isStarted)
			return;
		if (!isWifiNetworkReady())
			return;
		
        WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        m_lock = wifi.createMulticastLock("mylockthereturn");
        m_lock.setReferenceCounted(true);
        m_lock.acquire();
        try {
            m_jmdns = JmDNS.create();
            m_jmdns.addServiceListener(RDIALER_SERVICE_TYPE, m_listener = new ServiceListener() {

                @Override
                public void serviceResolved(ServiceEvent ev) {
                	System.out.println("Service resolved: " + ev.getInfo().getName() + " port:" + ev.getInfo().getPort());
                	RemoteDevice device = new RemoteDevice().Init(ev.getInfo());
                	if (!m_devices.contains(device))
                	{
                		m_devices.add(device);
                		reportNewDevices();
                	}
                }

                @Override
                public void serviceRemoved(ServiceEvent ev) {
                	System.out.println("Service removed: " + ev.getName());
                	RemoteDevice device = new RemoteDevice().Init(ev.getInfo());
                	if (m_devices.contains(device))
                	{
                		m_devices.remove(device);
                		reportNewDevices();
                	}
                }

                @Override
                public void serviceAdded(ServiceEvent event) {
                    // Required to force serviceResolved to be called again (after the first search)
                    m_jmdns.requestServiceInfo(event.getType(), event.getName(), 1);
                }
            });
            ServerSocket socket = new ServerSocket(0);
            m_serviceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE, m_deviceName, socket.getLocalPort(), RDIALER_SERVICE_DESCRIPTION);
            m_jmdns.registerService(m_serviceInfo);
            new CommandReceiveTask().execute(socket);
            System.out.println("Service started");
            m_isStarted = true;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
		
	}
	
    private void dialNumber(String number)
    {
    	Intent callIntent = new Intent(Intent.ACTION_CALL);
    	callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	callIntent.setData(Uri.parse("tel:" + number));
    	startActivity(callIntent);
    }

	public void onCreate() 
	{
	    super.onCreate();
	    // TODO: получать имя из настроек
	    m_deviceName = DEFAULT_DEVICE_NAME;
	    m_devices = new ArrayList<RemoteDevice>();

	    // Регистрируем слушателя состояния сети wifi
	    m_connStateReceiver = new ConnectionStateReceiver();
	    IntentFilter intentFilter = new IntentFilter();
	    //intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
	    intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
	    registerReceiver(m_connStateReceiver, intentFilter);
	    
	    System.out.println("onCreate(): name=" + m_deviceName);
	}	
	
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		System.out.println("onStartCommand");
		if (intent == null)
			return START_STICKY;
		try
		{
			PendingIntent pi = intent.getParcelableExtra(CMD_PENDING_EXTRA);
			switch(intent.getIntExtra(CMD_EXTRA, CMD_NONE))
			{
			case CMD_START:
		    	startRemoteDialerService();
		    	break;
			case CMD_GET_DEVICES:
				if (pi != null)
				{
			    	startRemoteDialerService();
					Intent clientIntent = new Intent().putExtra(DEVICES_EXTRA, m_devices);
					pi.send(this, CMD_RES_SUCCESS, clientIntent);
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
		return START_STICKY;
	}

	public void onDestroy() 
	{
		stopRemoteDialerService();
        unregisterReceiver(m_connStateReceiver);
	}

}
