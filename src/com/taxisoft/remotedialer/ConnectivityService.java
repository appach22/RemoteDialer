package com.taxisoft.remotedialer;

public abstract class ConnectivityService
{
	protected final static int STATE_STOPPED 	= 1;
	protected final static int STATE_RUNNING 	= 2;
	
	protected RemoteDialerService mParentService;
	protected int mServiceState;
	
	public ConnectivityService(RemoteDialerService parentService)
	{
		mParentService = parentService;
		mServiceState = STATE_STOPPED;
	}
	
	public void start()
	{
		
	}
	
	public void stop()
	{
		
	}

}
