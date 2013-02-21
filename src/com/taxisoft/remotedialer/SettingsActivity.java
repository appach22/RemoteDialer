package com.taxisoft.remotedialer;

import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.ViewById;

import android.os.Bundle;
import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.CheckBox;
import android.widget.EditText;

@EActivity(R.layout.activity_settings)
public class SettingsActivity extends Activity
{
	@ViewById
	EditText edtName;
	@ViewById
	CheckBox cbAutostart;
	
	SharedPreferences m_settings;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
    	m_settings = getSharedPreferences("RDialerPrefs", MODE_PRIVATE);
    	cbAutostart.setChecked(m_settings.getBoolean("autostart", true));
	}	
}
