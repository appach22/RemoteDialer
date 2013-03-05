package com.taxisoft.remotedialer;

import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

public class DeviceAdapter extends ArrayAdapter<RemoteDevice>
{
	private ArrayList<RemoteDevice> mAllDevices;
	private ArrayList<RemoteDevice> mCurrentDevices;
	private Context mContext;
	
	@SuppressWarnings("unchecked")
	public DeviceAdapter(Context context, int textViewResourceId,
			ArrayList<RemoteDevice> devices)
	{
		super(context, textViewResourceId, devices);
		mAllDevices = (ArrayList<RemoteDevice>) devices.clone();
		mCurrentDevices = devices;
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
        RemoteDevice device = mCurrentDevices.get(position);
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
	
    @Override
    public Filter getFilter() {
        return nameFilter;
    }

    Filter nameFilter = new Filter() {
        public String convertResultToString(Object resultValue) {
            return resultValue.toString();
        }
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if(constraint != null) {
            	mCurrentDevices.clear();
                for (RemoteDevice device : mAllDevices) {
                    if(device.mName.toLowerCase().contains(constraint.toString().toLowerCase())){
                    	mCurrentDevices.add(device);
                    }
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = mCurrentDevices;
                filterResults.count = mCurrentDevices.size();
                return filterResults;
            } else {
                return new FilterResults();
            }
        }
        
		@Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results != null && results.count > 0) 
            {
            	notifyDataSetChanged();
            }
        	
        }
    };

}
