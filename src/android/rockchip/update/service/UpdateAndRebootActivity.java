package android.rockchip.update.service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

public class UpdateAndRebootActivity extends AlertActivity {
	private static final String TAG = "UpdateAndRebootActivity";

	private static void LOG(String msg) {
		Log.d(TAG, msg);
	}

	public final static int COMMAND_START_UPDATING = 1;

	private String mImageFilePath;
	private Context mContext;
	private UiHandler mUiHandler;
	private WorkHandler mWorkHandler;
	private RKUpdateService.LocalBinder mBinder;
	private static PowerManager.WakeLock wakeLock;
	PowerManager powerManager;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			LOG("onServiceConnected");
			mBinder = (RKUpdateService.LocalBinder) service;
			mWorkHandler.sendEmptyMessageDelayed(COMMAND_START_UPDATING, 3000);
		}

		public void onServiceDisconnected(ComponentName className) {
			LOG("onServiceDisconnected");
			mBinder = null;
		}
	};

	@SuppressLint("SdCardPath")
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		Intent startIntent = getIntent();
		mImageFilePath = startIntent.getExtras().getString(RKUpdateService.EXTRA_IMAGE_PATH);

		AlertController.AlertParams params = mAlertParams;
		params.mTitle = getString(R.string.updating_title);
		params.mIconId = R.drawable.ic_dialog_alert;
		String message = getText(R.string.updating_prompt).toString();
		if (mImageFilePath.contains("/sdcard")) {
			message += getText(R.string.updating_prompt_sdcard).toString();
		}
		params.mMessage = message;
		params.mPositiveButtonText = null;
		params.mPositiveButtonListener = null;
		params.mNegativeButtonText = null;
		params.mNegativeButtonListener = null;
		setupAlert();

		if (mImageFilePath.endsWith("img")) {
			try {
				LOG("RecoverySystem.installRKimage");
				RecoverySystem.installRKimage(mContext, mImageFilePath);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG("ERROR1!");
				e.printStackTrace();
				finish();
			}
        }/* else if (!RecoverySystem.verifyPackage(mImageFilePath)) {
        	LOG("Invalid Package!");
        	finish();
        }*/ else {
        	try {
        		LOG("RecoverySystem.installPackage");
				RecoverySystem.installPackage(mContext, new File(mImageFilePath));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG("ERROR2!");
				e.printStackTrace();
				finish();
			}
        }

		// powerManager = (PowerManager)
		// getSystemService(Context.POWER_SERVICE);
		// wakeLock = powerManager.newWakeLock(1, "ota-lock");

		// HandlerThread thread = new HandlerThread("UpdateAndRebootActivity :
		// work thread");
		// thread.start();
		// mWorkHandler = new WorkHandler(thread.getLooper());
		 mUiHandler = new UiHandler();
		// mContext.bindService(new Intent(mContext, RKUpdateService.class),
		// mConnection, Context.BIND_AUTO_CREATE);
	}

	protected void dialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setMessage(R.string.update_error_summary);
		builder.setTitle(R.string.update_error);
		builder.setPositiveButton(R.string.NIA_btn_ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				// Power.releaseWakeLock("ota-lock");
				LOG("dialog() : onClick");
				wakeLock.release();
				finish();
			}
		});
		AlertDialog alertDialog = builder.create();
		alertDialog.show();
	}

	protected void onPause() {
		super.onPause();
		// wakeLock.release();
		LOG("onPause() : Entered.");
	}

	protected void onResume() {
		super.onResume();
		// Power.acquireWakeLock(1, "ota-lock");
		// wakeLock.acquire();
	}

	@SuppressLint("HandlerLeak")
	private class UiHandler extends Handler {
		public void handleMessage(Message msg) {
			LOG(msg.toString());
			switch (msg.what) {
			case COMMAND_START_UPDATING:
				LOG("UiHandler() : Entered.");
				dialog();
				break;
			}
		}
	}

	private class WorkHandler extends Handler {
		public WorkHandler(Looper looper) {
			super(looper);
		}

		public void handleMessage(Message msg) {
			LOG(msg.toString());
			switch (msg.what) {
			case COMMAND_START_UPDATING:
				LOG("WorkHandler::handleMessage() : To perform 'COMMAND_START_UPDATING'.");
				if (mBinder != null) {
					if (mImageFilePath.endsWith("img")) {
						LOG(".img");
						mBinder.updateFirmware(mImageFilePath, RKUpdateService.COMMAND_INSTALL_RKIMAGE);
					} else
						LOG(".zip");
					if (!mBinder.doesOtaPackageMatchProduct(mImageFilePath)) {
						mUiHandler.sendEmptyMessage(COMMAND_START_UPDATING);
					} else {
						mBinder.updateFirmware(mImageFilePath, RKUpdateService.COMMAND_INSTALL_PACKAGE);
					}
				} else {
					Log.d(TAG, "service have not connected!");
				}
				break;
			}
		}
	}
}
