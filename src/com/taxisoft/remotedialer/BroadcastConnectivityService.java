package com.taxisoft.remotedialer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.googlecode.androidannotations.api.BackgroundExecutor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;

public class BroadcastConnectivityService extends ConnectivityService
{
	private InetAddress mBroadcastAddr = null;
	private DatagramSocket mIncomingSocket = null;
	private AlarmManager mAlarmManager = null;
	private PendingIntent mPendingAlarmIntent;
	
	public BroadcastConnectivityService(RemoteDialerService parentService)
	{
		super(parentService);
		stop();
	}

	// Запускает периодическую отправку UDP-броадкаст пакетов с информацией о себе
	private boolean startBroadcasting()
	{
		// Если с устройства не позвонить - не будем о себе сообщать
		if (!mParentService.isPhoneAvailable())
			return false;

		// Получаем броадкаст адрес у WiFi-менеджера
        WifiManager wifi = (android.net.wifi.WifiManager)mParentService.getSystemService(android.content.Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // Если DHCP информацию получить не удалось - выходим
        if (dhcp == null)
        	return false;
        
        // Получаем сервис Alarm Manager'а
        mAlarmManager = (AlarmManager)mParentService.getSystemService(Context.ALARM_SERVICE);
        // Если не получили - выходим
        if (mAlarmManager == null)
        	return false;
        
    	int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] byteaddr = new byte[] { (byte) (broadcast & 0xff), (byte) (broadcast >> 8 & 0xff),
                  (byte) (broadcast >> 16 & 0xff), (byte) (broadcast >> 24 & 0xff) };
        try
		{
        	// Подготавливаем intent с информацией о девайсе
			mBroadcastAddr = InetAddress.getByAddress(byteaddr);
			Intent intent = new Intent(mParentService, UdpAlarmReceiver.class);
			intent.putExtra("broadcast_ip", mBroadcastAddr.getHostAddress());
			intent.putExtra("device_name", mParentService.getDevices().mThisDeviceName);
			intent.putExtra("device_uid", mParentService.getDevices().mThisDeviceUid);
			intent.putExtra("device_desc", RemoteDialerDevices.DEFAULT_DEVICE_NAME);
			mPendingAlarmIntent = PendingIntent.getBroadcast(mParentService, 0, intent, 0);
			// Запускаем Alarm с периодичностью в минуту начиная с текущего момента времени 
			mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60, mPendingAlarmIntent);

			// Попросим всех, кто не спит, откликнуться
			requestOthersInfo();
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
			return false;
		}
        return true;
    }
	
	private void sendMyInfo()
	{
		try
		{
			String info = "DeviceInfo|" + 
					  mParentService.getDevices().mThisDeviceName + "|" +
					  mParentService.getDevices().mThisDeviceUid + "|" +
					  RemoteDialerDevices.DEFAULT_DEVICE_NAME;
			if (mBroadcastAddr != null)
				sendDatagram(mBroadcastAddr, info);
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private void requestOthersInfo()
	{
		try
		{
			sendDatagram(mBroadcastAddr, "GetDeviceInfo");
		} catch (SocketException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void processBroadcastPackets()
	{
		System.out.println("Listening for new devices broadcast");
		try
		{
			String infoFromPacket;
            byte[] receiveData = new byte[1024];
            mIncomingSocket = new DatagramSocket(RemoteDialerService.RDIALER_SERVICE_PORT);
            //socket.setBroadcast(true);
			while(true)
			{
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				mIncomingSocket.receive(receivePacket);
				infoFromPacket = new String(receivePacket.getData(), 0, receivePacket.getLength());
			    System.out.println("Received UDP: " + infoFromPacket);
			    if (infoFromPacket.startsWith("DeviceInfo"))
			    {
			    	RemoteDevice device = new RemoteDevice().InitFromBroadcast(infoFromPacket, receivePacket.getAddress(), RemoteDialerService.RDIALER_SERVICE_PORT);
			    	//mParentService.getDevices().addDevice(device);
			    	mParentService.getDevices().addDevice(device);
			    }
			    else if (infoFromPacket.startsWith("GetDeviceInfo"))
			    {
			    	sendMyInfo();
			    }
			}
			
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	
	@Override
	public void start()
	{
		if (mServiceState != STATE_RUNNING)
		{
			super.start();
			
			// Запускаем рассылку широковещательных пакетов с информацией о себе
			startBroadcasting();
			
	        // Запускаем фоновую обработку broadcast запросов с информацией об устройствах
	        BackgroundExecutor.execute(new Runnable() {
	            @Override
	            public void run() {
	                try {
	        			processBroadcastPackets();
	                } catch (RuntimeException e) {
	        			e.printStackTrace();
	                }
	            }
	        });
	        mServiceState = STATE_RUNNING;
	        System.out.println("BroadcastConnectivityService started");
		}
	}
	
	@Override
	public void stop()
	{
		super.stop();
		if (mIncomingSocket != null)
		{
			mIncomingSocket.close();
			mIncomingSocket = null;
		}
		if (mAlarmManager != null)
		{
			mAlarmManager.cancel(mPendingAlarmIntent);
			mPendingAlarmIntent.cancel();
			mAlarmManager = null;
		}
		// Пробуем всегда остановить, т.к. событие Alarm не привязано к сервису
		// и однажды запущенное будет работать всегда, если его явно не остановить
		else
		{
	        // Получаем сервис Alarm Manager'а
	        mAlarmManager = (AlarmManager)mParentService.getSystemService(Context.ALARM_SERVICE);
	        // Если не получили - выходим
	        if (mAlarmManager != null)
	        {
				Intent intent = new Intent(mParentService, UdpAlarmReceiver.class);
				mPendingAlarmIntent = PendingIntent.getBroadcast(mParentService, 0, intent, 0);
				mAlarmManager.cancel(mPendingAlarmIntent);
				mPendingAlarmIntent.cancel();
				mAlarmManager = null;
	        }
		}
		mBroadcastAddr = null;
        mServiceState = STATE_STOPPED;
        System.out.println("BroadcastConnectivityService stopped");
	}

	static void sendDatagram(InetAddress address, String message) throws IOException
	{
		byte[] sendData = message.getBytes();
		DatagramSocket broadcastSocket;
		broadcastSocket = new DatagramSocket();
		broadcastSocket.setBroadcast(true);
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, RemoteDialerService.RDIALER_SERVICE_PORT);
		broadcastSocket.send(sendPacket);
		broadcastSocket.close();
	}
}
