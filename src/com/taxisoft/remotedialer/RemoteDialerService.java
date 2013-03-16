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

import com.googlecode.androidannotations.annotations.Background;
import com.googlecode.androidannotations.annotations.EService;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.telephony.TelephonyManager;

@EService
public class RemoteDialerService extends Service  
{

	//private final static String LOG_TAG = "RemoteDialerService";
    public final static int RDIALER_SERVICE_PORT = 52836;
    //private final static String RDIALER_SERVICE_DESCRIPTION = "Remote Dialer service";
	protected final static String CMD_EXTRA = "command";
	protected final static String CMD_PARAM_EXTRA = "command parameter";
	protected final static String CMD_PENDING_EXTRA = "pending";
	
	protected final static int CMD_NONE 						= 0;
	protected final static int CMD_START						= 1;
	protected final static int CMD_RESTART						= 2;
	protected final static int CMD_GET_DEVICES 					= 3;
	protected final static int CMD_GET_FOUND_DEVICES			= 4;
	protected final static int CMD_DIAL_NUMBER					= 5;
	protected final static int CMD_UPDATE_THIS_DEVICE_NAME		= 6;
	protected final static int CMD_UPDATE_DEFAULT_DEVICE_NAME	= 7;

	protected final static int CMD_RES_SUCCESS		= 0;
	protected final static int CMD_RES_NO_SUCH_CMD	= 1;
	protected final static int CMD_RES_FAILURE		= 2;

	private final static int STATE_STOPPED 	= 1;
	private final static int STATE_STARTING = 2;
	private final static int STATE_RUNNING 	= 4;
	private final static int STATE_STOPPING = 8;

	private static int mServiceState = STATE_STOPPED;
	private ConnectionStateReceiver mConnStateReceiver;	
	private ServerSocket mCommandServerSocket;
	private RemoteDialerDevices mDevices;
	private MdnsConnectivityService mMdnsService;
	private BroadcastConnectivityService mBroadcastService;
	private boolean mCommandServerRunning = false;

	public RemoteDialerService() 
	{
		super();
	}
	
  
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
	

	private boolean isWifiNetworkReady()
	{
		ConnectivityManager connManager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
        if (info != null)
        {
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
	
	public boolean isPhoneAvailable()
	{
		if (((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getPhoneType()
			    == TelephonyManager.PHONE_TYPE_NONE)		
			return false;
		else
			return true;
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
	
	
	private void stopRemoteDialerService()
	{
		System.out.println("STOP " + this + " " + mServiceState);
		if (mServiceState == STATE_STOPPING || mServiceState == STATE_STOPPED)
			return;
		if (mServiceState == STATE_STARTING)
			if (waitForServiceState(STATE_RUNNING | STATE_STOPPED) == STATE_STOPPED)
				return;
		
		try
		{
			mServiceState = STATE_STOPPING;
			// Удаляем из списка все устройства, кроме локального
			mDevices.removeAllExceptLocal();
			// Останавливаем службу MDNS
			mMdnsService.stop();
			// Останавливаем службу Broadcast
			mBroadcastService.stop();
			// Останавливаем сервер приема команд
			mCommandServerSocket.close();
			// Ожидаем останова сервера приема команд
			while (mCommandServerRunning)
				try
				{
					Thread.sleep(100);
				} catch (InterruptedException e)
				{
					e.printStackTrace();
				}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
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

		// Проверяем доступность сети Wi-Fi
		if (isWifiNetworkReady())
		{		
			// Если сеть доступна - будем слушать сервисы RemoteDialer в локальной сети
			try 
			{
	            // Запускаем фоновую обработку запросов
	            mCommandServerSocket = new ServerSocket(RemoteDialerService.RDIALER_SERVICE_PORT);
	            processRequest(mCommandServerSocket);
	            
	            System.out.println("Network is ready. Starting services...");
				// Запускаем службу и поисковик на базе MDNS
				//mMdnsService.start();
				
				// Запускаем службу и поисковик на базе Broadcast
				mBroadcastService.start();
				
	            System.out.println("Service started. Name=" + mDevices.mThisDeviceName);
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
	    mServiceState = STATE_STOPPED;

	    // Создаем список устройств
	    mDevices = new RemoteDialerDevices(this);
	    // Подготавливаем список устройств
		mDevices.updateFromSettings();
		// Если сотовая связь доступна, добавляем локальное устройство в список
		if (isPhoneAvailable())
		{
			System.out.println("Adding local device...");
			mDevices.addLocal();
		}
		
	    // Создаем сервис MDNS
	    mMdnsService = new MdnsConnectivityService(this);

	    // Создаем сервис Broadcast
	    mBroadcastService = new BroadcastConnectivityService(this);

	    // Регистрируем слушателя состояния сети wifi (обязательно в отдельном потоке, т.к. network
 	    mConnStateReceiver = new ConnectionStateReceiver();
	    IntentFilter intentFilter = new IntentFilter();
	    //intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
	    intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
	    registerReceiver(mConnStateReceiver, intentFilter);   
	}	
	
	@Background
	protected void processRequest(ServerSocket socket)
	{
		System.out.println("Listening for incoming commands");
        mCommandServerRunning = true;
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
			    	serverReply = mDevices.mThisDeviceName + '\n';
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
		System.out.println("Command server stopped");	
        mCommandServerRunning = false;
	}
	
	protected void processCommand(Intent intent)
	{
		try
		{
			PendingIntent pi = intent.getParcelableExtra(CMD_PENDING_EXTRA);
			//System.out.println("processCommand(): " + intent.getIntExtra(CMD_EXTRA, CMD_NONE));
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
					Intent clientIntent = new Intent().putParcelableArrayListExtra(RemoteDialerDevices.DEVICES_EXTRA, mDevices);
					pi.send(this, CMD_RES_SUCCESS, clientIntent);
				}
				break;
			case CMD_GET_FOUND_DEVICES:
				if (pi != null)
				{
					Intent clientIntent = new Intent().putParcelableArrayListExtra(RemoteDialerDevices.DEVICES_EXTRA, mDevices);
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
			case CMD_UPDATE_THIS_DEVICE_NAME:
				mDevices.updateThisDeviceName(intent.getStringExtra(CMD_PARAM_EXTRA));
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
	
	public RemoteDialerDevices getDevices()
	{
		return mDevices;
	}
}
