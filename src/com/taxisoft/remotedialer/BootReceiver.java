package com.taxisoft.remotedialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver
{

	public BootReceiver()
	{
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (context.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).getBoolean("autostart", true))
			context.startService(new Intent(context, RemoteDialerService.class)
			.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_START));	
	}

}
