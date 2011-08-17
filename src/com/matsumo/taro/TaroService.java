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

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;

public class TaroService extends Service {
	private LayoutParams params3;
	private WindowManager wm;
	private NotificationManager notificationManager;
	private Camera camera;
	private static byte[] cameraBuffer = null;
	private ImageView iView;
	private int cnt=0;
	private int pwidth = 176;	// プレビューの幅 
	private int pheight = 144;	// プレビューの高さ 
	private Bitmap pbmp; 
	private boolean isLand = false;

	@Override
	public void onCreate() {
		super.onCreate();
//		Toast.makeText(getApplicationContext(), "onCreate", Toast.LENGTH_LONG).show();
		wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
		notificationManager = (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		int height = pref.getInt("alpha", 64/*1*/);
		Configuration config = getResources().getConfiguration();
		isLand = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		
		iView = new ImageView(getApplicationContext());
		iView.setScaleType(ScaleType.CENTER_CROP/*FIT_CENTER/*CENTER*/);
//			iView.setMinimumHeight(320/*height*/);
		iView.setAlpha(height/*64*/);
//		iView.setImageBitmap(b);
		params3 = new LayoutParams();
		// 全画面
		params3.width = LayoutParams.FILL_PARENT;
		params3.height = LayoutParams.FILL_PARENT;
		// SystemAlert
		params3.type = LayoutParams.TYPE_SYSTEM_OVERLAY;
		// 透過
		params3.format = PixelFormat.TRANSLUCENT;
		// フルスクリーン
		params3.flags = /*LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |*/
			   LayoutParams.FLAG_LAYOUT_IN_SCREEN /*|
			   LayoutParams.FLAG_LAYOUT_NO_LIMITS |
			   LayoutParams.FLAG_NOT_TOUCH_MODAL |
			   LayoutParams.FLAG_NOT_FOCUSABLE*/;
		params3.gravity = Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL;

		camera = Camera.open();
		Camera.Parameters parameters = camera.getParameters();
		List<Size> pre = parameters.getSupportedPreviewSizes();
		int ii = 0;
		for(int i=0; i<pre.size(); i++){
			System.out.println(String.format("x=%d,y=%d", pre.get(i).width, pre.get(i).height));
			if(pre.get(ii).width > pre.get(i).width) ii = i;
		}
		pwidth = pre.get(ii).width;
		pheight = pre.get(ii).height;
		pbmp = Bitmap.createBitmap(pwidth, pheight, Bitmap.Config.ARGB_8888);  // ARGB8888で空のビットマップ作成 
		cameraBuffer = new byte[pwidth*pheight*3/2+16];	//1px=12bit (+16は保険)
//		Toast.makeText(getApplicationContext(), String.format("w=%d,h=%d", pwidth, pheight), Toast.LENGTH_SHORT).show();
		parameters.setPreviewSize(pwidth , pheight);
		camera.setParameters(parameters);
//		  camera.setPreviewDisplay(holder);
		camera.addCallbackBuffer(cameraBuffer);
		camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback(){
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				if(cnt % 4 == 0){
					int[] rgb = new int[(pwidth * pheight)];  // ARGB8888の画素の配列 
					try { 
							decodeYUV420SP(rgb, data, pwidth, pheight);	 // 変換 
							pbmp.setPixels(rgb, 0, pwidth, 0, 0, pwidth, pheight);	// 変換した画素からビットマップにセット 
							// 保存したり、表示したり 
							if(isLand){
								//横→そのまま表示
								BitmapDrawable bmd = new BitmapDrawable(pbmp);
								iView.setImageDrawable(bmd);
/*								Toast toast = new Toast(getApplicationContext());  
								toast.setDuration(Toast.LENGTH_SHORT);	
								toast.setView(iView);  
								toast.show();  */
							}else{
								//縦→90度回転して表示
								Matrix mtx = new Matrix();
								mtx.postRotate(90);
								Bitmap rotatedBMP = Bitmap.createBitmap(pbmp, 0, 0, pwidth, pheight, mtx, true);
								BitmapDrawable bmd = new BitmapDrawable(rotatedBMP);
								iView.setImageDrawable(bmd);
							}
					} catch (Exception e) { 
						e.printStackTrace();
					} 
				}
				cnt++;
//				System.out.println("onPreviewFrame:"+arg0.length);
				camera.addCallbackBuffer(cameraBuffer);
			}
		});
		camera.startPreview();

		start();
		setNotifyIcon(R.drawable.icon, getText(R.string.app_name).toString(), true);
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		//normal
//		Toast.makeText(getApplicationContext(), "onStart()", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onDestroy() {
//		Toast.makeText(getApplicationContext(), "onDestroy", Toast.LENGTH_LONG).show();
		stop();

		camera.setPreviewCallback(null);
		camera.stopPreview();
		camera.release();
		camera = null;
		pbmp.recycle();
		pbmp = null;
		
		notificationManager.cancelAll();
		stopForeground(true);
//		mView = null;
		super.onDestroy();
	}

	class TaroBinder extends Binder {
		TaroService getService() {
			return TaroService.this;
		}
	}
	@Override
	public IBinder onBind(Intent intent) {
		return new TaroBinder();
	}
	
	@Override
	public void onRebind(Intent intent) {
//		Toast toast = Toast.makeText(getApplicationContext(), "onRebind()", Toast.LENGTH_SHORT);
//		toast.show();
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
//		Toast toast = Toast.makeText(getApplicationContext(), "onUnbind()", Toast.LENGTH_SHORT);
//		toast.show();
		return true; // 再度クライアントから接続された際に onRebind を呼び出させる場合は true を返す
	}

	@Override
	public void onConfigurationChanged(android.content.res.Configuration newConfig) {
		isLand = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		super.onConfigurationChanged(newConfig);
	}

	/**
	 * Uses the given context to determine whether the service is already running.
	 */
	public static boolean isRunning(Context context) {
		ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

		for (RunningServiceInfo serviceInfo : services) {
			ComponentName componentName = serviceInfo.service;
			String serviceName = componentName.getClassName();
			if (serviceName.equals(TaroService.class.getName())) {
				return true;
			}
		}

		return false;
	}

	public void start(){
		// WindowManagerに追加
		wm.addView(iView, params3);
	}
	
	public void stop(){
		wm.removeView(iView);
	}
	
	private void setNotifyIcon(int id, String status, boolean first){
		Intent intent = new Intent(this, TaroActivity.class);
		PendingIntent contentIntent =
				PendingIntent.getActivity(this, 0, intent, 0);
		Notification notification = new Notification(
				id,
				getText(R.string.app_name),
				System.currentTimeMillis());
		notification.setLatestEventInfo(
				this,
				status,
				"タップすると画面を表示します",
				contentIntent);
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		startForeground(0, notification);
		notificationManager.notify(0, notification);
	}

	/**
	 * YUV420データをBitmapに変換します
	 * @param rgb
	 * @param yuv420sp
	 * @param width
	 * @param height
	 */
	private static final void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;
		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0) y = 0;
				if ((i & 1) == 0) {
						v = (0xff & yuv420sp[uvp++]) - 128;
						u = (0xff & yuv420sp[uvp++]) - 128;
				}
				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);
				if (r < 0) r = 0; else if (r > 262143) r = 262143;
				if (g < 0) g = 0; else if (g > 262143) g = 262143;
				if (b < 0) b = 0; else if (b > 262143) b = 262143;
				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}
}
