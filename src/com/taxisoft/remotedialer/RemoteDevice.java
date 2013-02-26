package com.taxisoft.remotedialer;

import javax.jmdns.ServiceInfo;

import com.taxisoft.remotedialer.RemoteDevice;

import android.os.Parcel;
import android.os.Parcelable;

class RemoteDevice extends Object implements Parcelable
{
	protected final static int DEVICE_TYPE_NONE				= 0; 
	protected final static int DEVICE_TYPE_THIS				= 1; 
	protected final static int DEVICE_TYPE_LOCAL_NETWORK	= 2; 
	
	public String mName;
	public int mType;
	public String mHost;
	public int mPort;
	public String mModel;

	public RemoteDevice()
	{
		mName = "Undefined";
		mType = DEVICE_TYPE_NONE;
		mHost = "";
		mPort = 0;
	}
	
	@Override
    public boolean equals(Object obj) 
	{
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        return mName.equalsIgnoreCase(((RemoteDevice)obj).mName);
	}
	
	public RemoteDevice Init(ServiceInfo info)
	{
		mName = info.getName();
		mType = DEVICE_TYPE_LOCAL_NETWORK;
		mHost = info.getHostAddresses()[0];
		mPort = info.getPort();
		mModel = info.getNiceTextString();
		return this;
	}

	public RemoteDevice InitLocal(String deviceName)
	{
		mName = deviceName;
		mType = DEVICE_TYPE_THIS;
		return this;
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags)
	{
		//System.out.println("to parcel " + m_name);
		out.writeString(mName);
		out.writeInt(mType);
		out.writeString(mHost);
		out.writeInt(mPort);
		out.writeString(mModel);
	}
	
	public final static Parcelable.Creator<RemoteDevice> CREATOR 
		= new Parcelable.Creator<RemoteDevice>() {
			public RemoteDevice createFromParcel(Parcel in) {
				return new RemoteDevice(in);
			}
			public RemoteDevice[] newArray(int size) {              
				return new RemoteDevice[size];          
			}      
		}; 				
	
	private RemoteDevice(Parcel in) 
	{
		//System.out.println("from parcel");
		mName = in.readString();  
		mType = in.readInt();
		mHost = in.readString();
		mPort = in.readInt();
		mModel = in.readString();
	}
	
	@Override
	public String toString()
	{
		//System.out.println("toString " + m_name);
		return mName + " " + mModel + " (" + mPort + ")";
	}
}
