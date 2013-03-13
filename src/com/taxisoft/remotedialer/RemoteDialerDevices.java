package com.taxisoft.remotedialer;

import java.util.ArrayList;
import java.util.Random;

import javax.jmdns.ServiceInfo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;

public class RemoteDialerDevices
{
	public final static String DEVICES_EXTRA = "devices";
	protected final static String DEVICES_BROADCAST = "com.taxisoft.remotedialer.devices";
	protected final static String DEFAULT_DEVICE_NAME = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;

	public String mThisDeviceName;
	public String mThisDeviceUid;
	private ArrayList<RemoteDevice> mDevices;
	private Context mContext; 
	// TODO: помещать устройство по умолчанию в начало списка
	private String mDefaultDeviceName;

	public RemoteDialerDevices(Context context)
	{
		mContext = context;
		
	    mDevices = new ArrayList<RemoteDevice>();

	    // Получаем uid данного устройства
	    mThisDeviceUid = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).getString("uid", "");
	    if (mThisDeviceUid.equals(""))
	    {
	    	// Если это первый запуск - то создаем этот uid
	    	mThisDeviceUid = Long.toString(new Random().nextLong());
    		Editor e = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).edit();
    		e.putString("uid", mThisDeviceUid);
    		e.commit();
	    }
	}
	
	public void updateFromSettings()
	{
		// Получаем из настроек имя устройства
	    mThisDeviceName = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE)
	    		.getString("device_name", DEFAULT_DEVICE_NAME);
	    
		// Получаем из настроек имя устройства по умолчанию
	    mDefaultDeviceName = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE)
	    		.getString("default_device_name", DEFAULT_DEVICE_NAME);
	    
		
	}

	public void addDevice(ServiceInfo info)
	{
		addDevice(new RemoteDevice().Init(info));
	}
	
	public void addDevice(RemoteDevice device)
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
	
	public void removeDevice(ServiceInfo info)
	{
    	RemoteDevice device = new RemoteDevice().Init(info);
    	if (mDevices.contains(device))
    	{
    		mDevices.remove(device);
    		reportNewDevices();
    	}
	}

	private void reportNewDevices()
	{
		Intent clientIntent = new Intent(DEVICES_BROADCAST);
    	clientIntent.putExtra(DEVICES_EXTRA, mDevices);
    	mContext.sendBroadcast(clientIntent);	
	}
	
	public void removeAllExceptLocal()
	{
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
	}
	
	public void addLocal()
	{
		addDevice(new RemoteDevice().InitLocal(mThisDeviceName, mThisDeviceUid));		
	}
	
	public ArrayList<RemoteDevice> getDevices()
	{
		return mDevices;
	}
	
//	private void updateDevicesWithNewList(ServiceInfo infos[])
//	{
//		ArrayList<RemoteDevice> tmpDevices = new ArrayList<RemoteDevice>(); 
//		for (int i = 0; i < infos.length; ++i)
//		{
//			RemoteDevice device = new RemoteDevice().Init(infos[i]);
//			tmpDevices.add(device);
//		}
//		
//	}
	
}
