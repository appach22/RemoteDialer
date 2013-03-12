package com.taxisoft.remotedialer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EService;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

@EService
public class RemoteDialerService extends Service  
{

	public final static String DEFAULT_DEVICE_NAME = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
	//private final static String LOG_TAG = "RemoteDialerService";
    private final static String RDIALER_SERVICE_TYPE = "_rdialer._tcp.local.";
    public final static int RDIALER_SERVICE_PORT = 52836;
    //private final static String RDIALER_SERVICE_DESCRIPTION = "Remote Dialer service";
	protected final static String DEVICES_EXTRA = "devices";
	protected final static String DEVICES_BROADCAST = "com.taxisoft.remotedialer.devices";
	protected final static String CMD_EXTRA = "command";
	protected final static String CMD_PARAM_EXTRA = "command parameter";
	protected final static String CMD_PENDING_EXTRA = "pending";
	
	protected final static int CMD_NONE 				= 0;
	protected final static int CMD_START				= 1;
	protected final static int CMD_RESTART				= 2;
	protected final static int CMD_GET_DEVICES 			= 3;
	protected final static int CMD_GET_FOUND_DEVICES	= 4;
	protected final static int CMD_DIAL_NUMBER			= 5;

	protected final static int CMD_RES_SUCCESS		= 0;
	protected final static int CMD_RES_NO_SUCH_CMD	= 1;
	protected final static int CMD_RES_FAILURE		= 2;

	private final static int STATE_STOPPED 	= 1;
	private final static int STATE_STARTING = 2;
	private final static int STATE_RUNNING 	= 4;
	private final static int STATE_STOPPING = 8;

    private JmDNS mJmDNS = null;
    //private JmDNS mJmDNSListener = null;
    private MulticastLock mLock;
    private ServiceListener mListener = null;
	private String mDeviceName;
	// TODO: помещать устройство по умолчанию в начало списка
	private String mDefaultDeviceName;
	private ArrayList<RemoteDevice> mDevices;
	private static int mServiceState = STATE_STOPPED;
	private ConnectionStateReceiver mConnStateReceiver;	
	private String mThisDeviceUid;
	private Timer mListTimer;
	private Timer mBroadcastTimer = null;
	private InetAddress mBroadcastAddr = null;

	public RemoteDialerService() 
	{
		super();
	}
	
//	class CommandReceiveTask extends AsyncTask<ServerSocket, Void, Void>
//    {
//
//    	@Override
//    	protected Void doInBackground(ServerSocket... params)
//    	{
//    		
//    		try
//    		{
//    			String clientRequest;
//    	        String serverReply = "Unknown request\n";
//    			ServerSocket welcomeSocket = params[0];
//    			while(true)
//    			{
//    			    Socket connectionSocket = welcomeSocket.accept();
//    			    BufferedReader inFromClient =
//    			       new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
//    			    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
//    			    clientRequest = inFromClient.readLine();
//    			    System.out.println("Received: " + clientRequest);
//    			    if (clientRequest.equalsIgnoreCase("GetDeviceName"))
//    			    	serverReply = mDeviceName + '\n';
//    			    else if (clientRequest.contains("DialNumber"))
//    			    {
//    			    	dialNumber(clientRequest.split(" ")[1]);
//    			    	serverReply = "Accepted\n";
//    			    }
//    			    outToClient.writeBytes(serverReply);
//    			}
//    			
//    		} catch (IOException e)
//    		{
//    			e.printStackTrace();
//    		}
//    		return null;
//    }
    
    class ConnectionStateReceiver extends BroadcastReceiver
    {
		@Override
		public void onReceive(Context context, final Intent intent)
		{
			Runnable r = new Runnable() {
				public void run() {	        	
				    final String action = intent.getAction();
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
				    			startBroadcasting();
				    			return;
				    		}
				    	}
				    	else
				    		System.out.println("Network state error!");
				    	stopRemoteDialerService();
				    }
				}
		    };
			Thread t = new Thread(r);
			t.start();
		}
    }
	
	@Override
	public IBinder onBind(Intent arg0)
	{
		return null;
	}
	
	// Запускает периодическую отправку UDP-броадкаст пакетов с информацией о себе
	private void startBroadcasting()
	{
		// Получаем броадкаст адрес у WiFi-менеджера
        WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // Если DHCP информацию получить не удалось - выходим
        if (dhcp == null)
        	return;

        // Получаем сервис Alarm Manager'а
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        // Если не получили - выходим
        if (am == null)
        	return;
        
    	int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] byteaddr = new byte[] { (byte) (broadcast & 0xff), (byte) (broadcast >> 8 & 0xff),
                  (byte) (broadcast >> 16 & 0xff), (byte) (broadcast >> 24 & 0xff) };
        try
		{
        	// Подготавливаем intent с информацией о девайсе
			mBroadcastAddr = InetAddress.getByAddress(byteaddr);
			Intent intent = new Intent(this, UdpAlarmReceiver.class);
			intent.putExtra("broadcast_ip", mBroadcastAddr.getHostAddress());
			intent.putExtra("device_name", mDeviceName);
			intent.putExtra("device_uid", mThisDeviceUid);
			intent.putExtra("device_desc", DEFAULT_DEVICE_NAME);
			PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, 0);
			// Запускаем Alarm с периодичностью в минуту начиная с текущего момента времени 
			am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60, pi);			
			
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
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
			e.printStackTrace();
			return 0;
		}
		return mServiceState;
	}
	
	private void addDevice(ServiceInfo info)
	{
		addDevice(new RemoteDevice().Init(info));
	}
	
	private void addDevice(RemoteDevice device)
	{
    	// Если список уже содержит это устройство - обновляем его в списке (на случай, если порт поменялся)
		int index = mDevices.indexOf(device);
		if (index != -1)
		{
			RemoteDevice existingDevice = mDevices.get(index);
			// Пресекаем попытки затереть локальное устройство не локальным
			if (existingDevice.mType == RemoteDevice.DEVICE_TYPE_THIS &&
				device.mType != RemoteDevice.DEVICE_TYPE_THIS)
				return;
			// Удаляем уже существующее устройство
    		mDevices.remove(existingDevice);
		}
		// Добавляем новое и сообщаем GUI, что надо обновить список
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
	
	private void updateDevicesWithNewList(ServiceInfo infos[])
	{
		ArrayList<RemoteDevice> tmpDevices = new ArrayList<RemoteDevice>(); 
		for (int i = 0; i < infos.length; ++i)
		{
			RemoteDevice device = new RemoteDevice().Init(infos[i]);
			tmpDevices.add(device);
		}
		
	}
	
	private void stopRemoteDialerService()
	{
		System.out.println("STOP " + this + " " + mServiceState);
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
			if (mJmDNS != null)
			{
				if (mListener != null)
					mJmDNS.removeServiceListener(RDIALER_SERVICE_TYPE, mListener);
				mJmDNS.unregisterAllServices();
				mJmDNS.close();
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
		if (mLock != null && mLock.isHeld())
			mLock.release();
		mServiceState = STATE_STOPPED;
	}
	
	private void startRemoteDialerService()
	{
		System.out.println("START " + this + " " + mServiceState);
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
			addDevice(new RemoteDevice().InitLocal(mDeviceName, mThisDeviceUid));	
		
		// Проверяем доступность сети Wi-Fi
		if (isWifiNetworkReady())
		{		
			// Если сеть доступна - будем слушать сервисы RemoteDialer в локальной сети
	        WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
	        WifiInfo wifiinfo = wifi.getConnectionInfo();
	        int intaddr = wifiinfo.getIpAddress();
	        if (intaddr != 0)
	        {
		        try {
			        byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
			                  (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
					System.out.println("Binding to " + (short)byteaddr[0] + "." + (short)byteaddr[1] + "." + (short)byteaddr[2] + "." + (short)byteaddr[3]);
					InetAddress addr = InetAddress.getByAddress(byteaddr);
			        
			        mLock = wifi.createMulticastLock("mylockthereturn");
			        mLock.setReferenceCounted(true);
			        mLock.acquire();
			        
		            mJmDNS = JmDNS.create(addr);
		            //mJmDNSListener = JmDNS.create(addr);
		            
		    		// Если сотовая связь доступна, позиционируем себя как сервис
		            
		    		if (isPhoneAvailable())
		    		{
			            // Для начала зарегистрируем и удалим сервис со старыми параметрами 
			            // (это приходится делать для того, чтобы при перерегистрации с тем же именем
			            // клиенты получили новые параметры сервиса
			            int oldPort = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).getInt("port", 0);
			            
			            ServerSocket socket = new ServerSocket(oldPort);
			            ServiceInfo serviceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE, 
			            							      			 mDeviceName + "___" + oldPort,
			            							      			 socket.getLocalPort(),
			            							      			 DEFAULT_DEVICE_NAME + "___" + mThisDeviceUid);
			            
			            System.out.println("Unregistering old service...");
			            //Log.d("Service", "Unregistering old service...");
			            mJmDNS.registerService(serviceInfo);
			            mJmDNS.unregisterService(serviceInfo);
			            try
						{
							Thread.sleep(1000);
						} catch (InterruptedException e)
						{
							e.printStackTrace();
						}
			            // Теперь зарегистрируем сервис снова
			            System.out.println("Registering new service...");
			            socket = new ServerSocket(0/*RDIALER_SERVICE_PORT*/);
			            int newPort = socket.getLocalPort();
			            serviceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE, 
			            							      mDeviceName + "___" + newPort,
			            							      newPort,
			            							      DEFAULT_DEVICE_NAME + "___" + mThisDeviceUid);
			            mJmDNS.registerService(serviceInfo);
			    		Editor e = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).edit();
			    		e.putInt("port", newPort);
			    		e.commit();
			            // Запускаем фоновую обработку поисковых broadcast запросов
			            processBroadcastPacket();
			            // Запускаем фоновую обработку запросов
			            processRequest(socket);
		    		}
		    		
		    		//mListTimer = new Timer();
		    		//mListTimer.scheduleAtFixedRate(new TimerTask(){
		    			//@Override
		    			//public void run()
		    			//{
				            // Делаем list, дабы определить уже зарегистрированные сервисы
				            ServiceInfo services[] = mJmDNS.list(RDIALER_SERVICE_TYPE, 6000);
				            for (int i = 0; i < services.length; ++i)
				            	addDevice(services[i]);
		    			//}
		    		//}, 60, 60 * 1000);
		    		
		            mJmDNS.addServiceListener(RDIALER_SERVICE_TYPE, mListener = new ServiceListener() {
		
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
		                    // Required to force serviceResolved to be called again (after the first search)
		                	mJmDNS.requestServiceInfo(event.getType(), event.getName(), 1);
		                }
		            });
		            System.out.println("Service started. Name=" + mDeviceName);
		            mServiceState = STATE_RUNNING;
		        } catch (UnknownHostException e) 
		        {
					mServiceState = STATE_STOPPED;
					e.printStackTrace();
					return;
		        } catch (IOException e) {
					mServiceState = STATE_STOPPED;
		            e.printStackTrace();
		            return;
				}
	        }
	        else
	        	// Если адрес Wi-Fi нулевой
				mServiceState = STATE_STOPPED;
		}
		else //if (isWifiNetworkReady())   
			// Если недоступен Wi-Fi - Давай, до свидания!
			mServiceState = STATE_STOPPED;
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

	    // Регистрируем слушателя состояния сети wifi (обязательно в отдельном потоке, т.к. network
		//Runnable r = new Runnable() {
//		     public void run() {	        	
		 	    mConnStateReceiver = new ConnectionStateReceiver();
			    IntentFilter intentFilter = new IntentFilter();
			    //intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
			    intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
			    registerReceiver(mConnStateReceiver, intentFilter);
//		     }
		//};
		//Thread t = new Thread(r);
		//t.start();
	    
	    // Получаем uid данного устройства
	    mThisDeviceUid = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).getString("uid", "");
	    if (mThisDeviceUid.equals(""))
	    {
	    	// Если это первый запуск - то создаем этот uid
	    	mThisDeviceUid = Long.toString(new Random().nextLong());
    		Editor e = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).edit();
    		e.putString("uid", mThisDeviceUid);
    		e.commit();
	    }
	}	
	
	@Background
	protected void processRequest(ServerSocket socket)
	{
		System.out.println("Listening for TCP");
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
	protected void processBroadcastPacket()
	{
		System.out.println("Listening for UDP");
		try
		{
			String infoFromPacket;
            byte[] receiveData = new byte[1024];
            DatagramSocket socket = new DatagramSocket(RDIALER_SERVICE_PORT);
            //socket.setBroadcast(true);
			while(true)
			{
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				socket.receive(receivePacket);
				infoFromPacket = new String(receivePacket.getData(), 0, receivePacket.getLength());
			    System.out.println("Received UDP: " + infoFromPacket);
			    if (infoFromPacket.startsWith("DeviceInfo"))
			    {
			    	RemoteDevice device = new RemoteDevice().InitFromBroadcast(infoFromPacket, receivePacket.getAddress(), RDIALER_SERVICE_PORT);
			    	addDevice(device);
			    }
			}
			
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	protected void processCommand(Intent intent)
	{
		try
		{
			PendingIntent pi = intent.getParcelableExtra(CMD_PENDING_EXTRA);
			System.out.println("processCommand(): " + intent.getIntExtra(CMD_EXTRA, CMD_NONE));
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
			case CMD_GET_FOUND_DEVICES:
				if (pi != null)
				{
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
	
	public int onStartCommand(final Intent intent, int flags, int startId) 
	{
		super.onStartCommand(intent, flags, startId);
		if (intent == null)
			return START_STICKY;
		Runnable r = new Runnable(){
			@Override
			public void run()
			{
				processCommand(intent);
			}
		};
		Thread t = new Thread(r);
		t.start();
		return START_STICKY;
	}

	public void onDestroy() 
	{
		stopRemoteDialerService();
        unregisterReceiver(mConnStateReceiver);
	}
}
