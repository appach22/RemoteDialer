package com.taxisoft.remotedialer;

import javax.jmdns.ServiceInfo;

import com.taxisoft.remotedialer.RemoteDevice;

import android.os.Parcel;
import android.os.Parcelable;

class RemoteDevice extends Object implements Parcelable
{
	protected final static int DEVICE_TYPE_NONE				= 0; 
	protected final static int DEVICE_TYPE_LOCAL_NETWORK	= 1; 
	
	public String m_name;
	public int m_type;
	public String m_host;
	public int m_port;

	public RemoteDevice()
	{
		m_name = "Undefined";
		m_type = DEVICE_TYPE_NONE;
		m_host = "";
		m_port = 0;
	}
	
	@Override
    public boolean equals(Object obj) 
	{
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        return m_name.equalsIgnoreCase(((RemoteDevice)obj).m_name);
	}
	
	RemoteDevice Init(ServiceInfo info)
	{
		m_name = info.getName();
		m_type = DEVICE_TYPE_LOCAL_NETWORK;
		m_host = info.getHostAddresses()[0];
		m_port = info.getPort();
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
		out.writeString(m_name);
		out.writeInt(m_type);
		out.writeString(m_host);
		out.writeInt(m_port);
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
		m_name = in.readString();  
		m_type = in.readInt();
		m_host = in.readString();
		m_port = in.readInt();
	}
	
	@Override
	public String toString()
	{
		//System.out.println("toString " + m_name);
		return m_name;
	}
}
