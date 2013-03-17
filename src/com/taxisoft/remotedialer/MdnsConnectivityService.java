package com.taxisoft.remotedialer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;

public class MdnsConnectivityService extends ConnectivityService
{
    private final static String RDIALER_SERVICE_TYPE = "_rdialer._tcp.local.";
    
    private JmDNS mJmDNS = null;
    private MulticastLock mLock;
    private ServiceListener mListener = null;

	public MdnsConnectivityService(RemoteDialerService parentService)
	{
		super(parentService);
	}

	@Override
	public void start()
	{
		super.start();
        WifiManager wifi = (android.net.wifi.WifiManager)mParentService.getSystemService(android.content.Context.WIFI_SERVICE);
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
	            
	    		if (mParentService.isPhoneAvailable())
	    		{
		            // Для начала зарегистрируем и удалим сервис со старыми параметрами 
		            // (это приходится делать для того, чтобы при перерегистрации с тем же именем
		            // клиенты получили новые параметры сервиса
		            //int oldPort = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).getInt("port", 0);
		            
		            //mDNSServerSocket = new ServerSocket(oldPort);
//		            ServiceInfo serviceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE, 
//		            							      			 mDeviceName + "___" + oldPort,
//		            							      			 oldPort,//mDNSServerSocket.getLocalPort(),
//		            							      			RemoteDialerService.DEFAULT_DEVICE_NAME + "___" + mThisDeviceUid);
		            
//		            System.out.println("Unregistering old service...");
//		            mJmDNS.registerService(serviceInfo);
//		            mJmDNS.unregisterService(serviceInfo);
//		            try
//					{
//						Thread.sleep(1000);
//					} catch (InterruptedException e)
//					{
//						e.printStackTrace();
//					}
		            // Теперь зарегистрируем сервис снова
		            System.out.println("Registering new service...");
		            int newPort = RemoteDialerService.RDIALER_SERVICE_PORT;
		            ServiceInfo serviceInfo = ServiceInfo.create(RDIALER_SERVICE_TYPE, 
		            								  mParentService.getDevices().mThisDeviceName + "___" + newPort,
		            							      newPort,
		            							      RemoteDialerDevices.DEFAULT_DEVICE_NAME + "___" + mParentService.getDevices().mThisDeviceUid);
		            mJmDNS.registerService(serviceInfo);
//		    		Editor e = getSharedPreferences("RDialerPrefs", MODE_PRIVATE).edit();
//		    		e.putInt("port", newPort);
//		    		e.commit();

		    		// Делаем list, дабы определить уже зарегистрированные сервисы
		            ServiceInfo services[] = mJmDNS.list(RDIALER_SERVICE_TYPE, 6000);
		            for (int i = 0; i < services.length; ++i)
		            	mParentService.getDevices().addDevice(services[i]);
		    		
		            mJmDNS.addServiceListener(RDIALER_SERVICE_TYPE, mListener = new ServiceListener() {
		
		                @Override
		                public void serviceResolved(ServiceEvent ev) {
		                	System.out.println("Service resolved: " + ev.getInfo().getName() + " port:" + ev.getInfo().getPort());
		                	try
							{
								mParentService.getDevices().addDevice(ev.getInfo());
							} catch (Exception e)
							{
								e.printStackTrace();
							}
		                }
		
		                @Override
		                public void serviceRemoved(ServiceEvent ev) {
		                	System.out.println("Service removed: " + ev.getName());
		                	try
							{
								mParentService.getDevices().removeDevice(ev.getInfo());
							} catch (Exception e)
							{
								e.printStackTrace();
							}
		                }
		
		                @Override
		                public void serviceAdded(ServiceEvent event) {
		                    // Required to force serviceResolved to be called again (after the first search)
		                	mJmDNS.requestServiceInfo(event.getType(), event.getName(), 1);
		                }
		            });
		            
	    		}
	        } catch (UnknownHostException e) 
	        {
				e.printStackTrace();
				return;
	        } catch (IOException e) {
	            e.printStackTrace();
	            return;
			} catch (Exception e)
			{
				e.printStackTrace();
			}
        }
	}

	@Override
	public void stop()
	{
		super.stop();
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
		mJmDNS = null;
		if (mLock != null)
		{
			if (mLock.isHeld())
				mLock.release();
			mLock = null;
		}
	}
}
