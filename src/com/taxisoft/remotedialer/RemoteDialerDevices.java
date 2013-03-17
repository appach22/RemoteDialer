package com.taxisoft.remotedialer;

import java.util.ArrayList;
import java.util.Random;

import javax.jmdns.ServiceInfo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;

public class RemoteDialerDevices extends ArrayList<RemoteDevice> implements Parcelable
{
	private static final long serialVersionUID = 1L;
	public final static String DEVICES_EXTRA = "devices";
	protected final static String DEVICES_BROADCAST = "com.taxisoft.remotedialer.devices";
	protected final static String DEFAULT_DEVICE_NAME = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;

	public String mThisDeviceName;
	public String mThisDeviceUid;
	private Context mContext; 
	// TODO: помещать устройство по умолчанию в начало списка
	private String mDefaultDeviceName;
	private String mDefaultDeviceUid;

	public RemoteDialerDevices()
	{
		super();
		mContext = null;
	}
	
	public RemoteDialerDevices(Context context)
	{
		super();
		setContext(context);
	}
	
	private void setContext(Context context)
	{
		mContext = context;
		
	    // Получаем uid данного устройства
	    mThisDeviceUid = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).getString("uid", "");
	    if (mThisDeviceUid.equals(""))
	    {
	    	// Если это первый запуск - то создаем этот uid
	    	// FIXME: использовать BigInteger
	    	mThisDeviceUid = Long.toString(new Random().nextLong());
    		Editor e = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).edit();
    		e.putString("uid", mThisDeviceUid);
    		e.commit();
	    }
	}
	
	public void updateFromSettings() throws Exception
	{
		if (mContext == null)
			throw new Exception("Readonly instance");
		
		// Получаем из настроек имя устройства
	    mThisDeviceName = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE)
	    		.getString("device_name", DEFAULT_DEVICE_NAME);
	    
		// Получаем из настроек имя и uid устройства по умолчанию
	    mDefaultDeviceName = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE)
	    		.getString("default_device_name", DEFAULT_DEVICE_NAME);
	    mDefaultDeviceUid = mContext.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE)
	    		.getString("default_device_uid", DEFAULT_DEVICE_NAME);
	}
	
	public void updateThisDeviceName(String newName) throws Exception
	{
		if (mContext == null)
			throw new Exception("Readonly instance");
		
		for (int i = 0; i < size(); ++i)
		{
			RemoteDevice device = get(i);
			if (device.mType == RemoteDevice.DEVICE_TYPE_THIS && device.mName.equals(mThisDeviceName))
			{
				mThisDeviceName = newName;
				device.mName = mThisDeviceName;
			}
		}
		reportNewDevices();
	}

	public void updateDefaultDeviceNameAndUid(String newName) throws Exception
	{
		if (mContext == null)
			throw new Exception("Readonly instance");
		
		System.out.print("updateDefaultDeviceNameAndUid(): " + newName); 
		String[] nameAndUid = newName.split("\\|");
		mDefaultDeviceName = nameAndUid[0];
		if (nameAndUid.length > 1)
			mDefaultDeviceUid = nameAndUid[1];
		else
			mDefaultDeviceUid = "";
		reportNewDevices();
	}

	public void addDevice(ServiceInfo info) throws Exception
	{
		addDevice(new RemoteDevice().Init(info));
	}
	
	public void addDevice(RemoteDevice device) throws Exception
	{
    	// Если список уже содержит это устройство - обновляем его в списке (на случай, если порт или имя поменялись)
		int index = indexOf(device);
		if (index != -1)
		{
			RemoteDevice existingDevice = get(index);
			// Пресекаем попытки затереть локальное устройство не локальным
			if (existingDevice.mType == RemoteDevice.DEVICE_TYPE_THIS &&
				device.mType != RemoteDevice.DEVICE_TYPE_THIS)
				return;
			// Удаляем уже существующее устройство
    		remove(existingDevice);
		}
		// Добавляем новое и сообщаем GUI, что надо обновить список
		add(device);
		reportNewDevices();
	}
	
	public void removeDevice(ServiceInfo info) throws Exception
	{
    	RemoteDevice device = new RemoteDevice().Init(info);
    	if (contains(device))
    	{
    		remove(device);
    		reportNewDevices();
    	}
	}

	private void reportNewDevices() throws Exception
	{
		if (mContext == null)
			throw new Exception("Readonly instance");

		Intent clientIntent = new Intent(DEVICES_BROADCAST);
    	clientIntent.putParcelableArrayListExtra(DEVICES_EXTRA, this);
    	mContext.sendBroadcast(clientIntent);	
	}
	
	public void removeAllExceptLocal() throws Exception
	{
		// Удаляем из списка все устройства кроме локального (если таковое есть)
		RemoteDevice thisDevice = null;
		for (int i = 0; i < size(); ++i)
			if (get(i).mType == RemoteDevice.DEVICE_TYPE_THIS)
			{
				thisDevice = get(i);
				break;
			}
		clear();
		if (thisDevice != null)
			add(thisDevice);
		reportNewDevices();
	}
	
	public void addLocal() throws Exception
	{
		addDevice(new RemoteDevice().InitLocal(mThisDeviceName, mThisDeviceUid));		
	}
	
	public int getDefaultDeviceIndex()
	{
		return indexOf(new RemoteDevice().InitLocal(mDefaultDeviceName, mDefaultDeviceUid)); 
	}
	
	public int getLocalDeviceIndex()
	{
		return indexOf(new RemoteDevice().InitLocal(mThisDeviceName, mThisDeviceUid)); 
	}
	
	// ==============================================================================================
	// ============================== Parcelable stuff ==============================================
	// ==============================================================================================

	@Override
	public void writeToParcel(Parcel out, int flags)
	{
		out.writeString(mThisDeviceName);
		out.writeString(mThisDeviceUid);
		out.writeString(mDefaultDeviceName);
		out.writeString(mDefaultDeviceUid);
		out.writeInt(size());
		for (int i = 0; i < size(); ++i)
			out.writeParcelable(get(i), flags);
	}
	
	public final static Parcelable.Creator<RemoteDialerDevices> CREATOR 
	= new Parcelable.Creator<RemoteDialerDevices>() {
		public RemoteDialerDevices createFromParcel(Parcel in) {
			return new RemoteDialerDevices(in);
		}
		public RemoteDialerDevices[] newArray(int size) {              
			return new RemoteDialerDevices[size];          
		}      
	}; 				

	private RemoteDialerDevices(Parcel in) 
	{
		this();
		mThisDeviceName = in.readString();
		mThisDeviceUid = in.readString();
		mDefaultDeviceName = in.readString();
		mDefaultDeviceUid = in.readString();
		int size = in.readInt();
		for (int i = 0; i < size; ++i)
			add((RemoteDevice)in.readParcelable(RemoteDevice.class.getClassLoader()));
	}

	@Override
	public int describeContents()
	{
		return 0;
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
