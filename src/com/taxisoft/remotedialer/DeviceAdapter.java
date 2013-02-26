package com.taxisoft.remotedialer;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class DeviceAdapter extends ArrayAdapter<RemoteDevice>
{
	private ArrayList<RemoteDevice> mDevices;
	private Context mContext;
	
	public DeviceAdapter(Context context, int textViewResourceId,
			ArrayList<RemoteDevice> devices)
	{
		super(context, textViewResourceId, devices);
		mDevices = devices;
		mContext = context;
	}
	
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) 
    {
    	return getCustomView(position, convertView, parent);
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) 
    {
    	return getCustomView(position, convertView, parent);
    }
    
    public View getCustomView(int position, View convertView, ViewGroup parent) 
    {
        View v = convertView;
        if (v == null) 
        	{
            LayoutInflater vi = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.device_list_item, null);
        }
        RemoteDevice device = mDevices.get(position);
        if (device != null) 
        {
            TextView name = (TextView) v.findViewById(R.id.tvDeviceName);
            TextView model = (TextView) v.findViewById(R.id.tvDeviceModel);
            if (name != null) 
            {
            	name.setText(device.mName);                            
            }
            if(model != null)
            {
                  model.setText(device.mModel);
            }
        }
        return v;
    }
	

}
