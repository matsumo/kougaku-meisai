/**
 * Copyright (C) 2011 matsumo All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.matsumo.taro;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Toast;

public class TaroActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

		Button bb = (Button)findViewById(R.id.button1);
		bb.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intents = new Intent(TaroActivity.this, TaroService.class);
				if(TaroService.isRunning(TaroActivity.this)){
					stopService(intents);
					Toast.makeText(TaroActivity.this, "stop", Toast.LENGTH_LONG).show();
				}else{
					SeekBar sb = (SeekBar)findViewById(R.id.seekBar1);
					int h = sb.getProgress() + 32;
					Editor edit = PreferenceManager.getDefaultSharedPreferences(TaroActivity.this).edit();
					edit.putInt("alpha", h);
					edit.commit();

					startService(intents);
					Toast.makeText(TaroActivity.this, "start", Toast.LENGTH_LONG).show();
				}
			}
		});
		SeekBar sb = (SeekBar)findViewById(R.id.seekBar1);
		sb.setProgress(pref.getInt("alpha", 64/*1*/) - 32);
		CheckBox cb = (CheckBox)findViewById(R.id.checkBox1);
		cb.setChecked(pref.getBoolean("autoStart", false));
    }

	@Override
	protected void onPause() {
		CheckBox cb = (CheckBox)findViewById(R.id.checkBox1);
		Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
		edit.putBoolean("autoStart", cb.isChecked());
		edit.commit();

		super.onPause();
	}
}