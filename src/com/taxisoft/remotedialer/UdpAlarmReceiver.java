package com.taxisoft.remotedialer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class UdpAlarmReceiver extends BroadcastReceiver
{

	public UdpAlarmReceiver()
	{
		;
	}

	@Override
	public void onReceive(Context context, final Intent intent)
	{
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "udp_broadcast");
        // Осуществляем блокировку
        wl.acquire();

        Runnable r = new Runnable(){
        	@Override
        	public void run()
        	{
        		try
        		{
	    			InetAddress broadcastAddr = InetAddress.getByName(intent.getStringExtra("broadcast_ip"));
	    			String info = "DeviceInfo|" + 
	    						  intent.getStringExtra("device_name") + "|" +
	    						  intent.getStringExtra("device_uid") + "|" +
	    						  intent.getStringExtra("device_desc");
	    			byte[] sendData = info.getBytes();
	    			System.out.println("Broadcasting to " + broadcastAddr.getHostName()); 
	    			DatagramSocket broadcastSocket = new DatagramSocket();
	    			broadcastSocket.setBroadcast(true);
	    			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddr, RemoteDialerService.RDIALER_SERVICE_PORT);
	    			broadcastSocket.send(sendPacket);
	    	        broadcastSocket.close();
	    	        wl.release();
	    		} catch (IOException e)
	    		{
	    			e.printStackTrace();
	    	        wl.release();
	    		}
        	}
        };
        Thread t = new Thread(r);
        t.start();
	}

}
