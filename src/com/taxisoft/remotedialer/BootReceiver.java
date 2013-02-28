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
		System.out.println("Remote dialer service autostart");
		if (context.getSharedPreferences("RDialerPrefs", Context.MODE_PRIVATE).getBoolean("autostart", true))
		{
			System.out.println("Starting service...");
			context.startService(new Intent(context, RemoteDialerService_.class)
			.putExtra(RemoteDialerService.CMD_EXTRA, RemoteDialerService.CMD_START));
		}
	}

}
