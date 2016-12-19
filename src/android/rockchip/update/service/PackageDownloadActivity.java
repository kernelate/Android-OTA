package android.rockchip.update.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringTokenizer;

import org.apache.http.client.HttpClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RemoteViews;

@SuppressLint("Wakelock")
public class PackageDownloadActivity extends Activity {
    private String TAG = "PackageDownloadActivity";
    private String WAKELOCK_KEY = "myDownload";

    private final static int STATE_READY = 0;
    private final static int STATE_DOWNLOADING = 2;
    private final static int STATE_ERROR = 4;
    private int mState = STATE_READY;
    private int notification_id = 20110921;

    private static PowerManager.WakeLock mWakeLock;
    private Context mContext;
    private RKUpdateService.LocalBinder mBinder;
    private ResolveInfo homeInfo;
    private String mFileName;
    private HttpClient mHttpClient;
    private Notification mNotify;
    private NotificationManager mNotifyManager;
    private FileDownloadTask mTask;
    private URI mUri;
    private ProgressBar mProgressBar;
    private Button mBtnCancel;
    private Button mBtnControl;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBinder = (RKUpdateService.LocalBinder) service;
            mBinder.LockWorkHandler();
        }
        public void onServiceDisconnected(ComponentName className)  {
            mBinder = null;
        }
    };

    @SuppressWarnings("deprecation")
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.package_download);
        mContext = this;
        mContext.bindService(new Intent(mContext, RKUpdateService.class),
            mConnection, Context.BIND_AUTO_CREATE);
        Intent startIntent = getIntent();
        mUri = null;
        try {
//            mUri = new URI(startIntent.getStringExtra("uri"));
//            if(mUri.equals(null)){
        	String mOtaPackageName = getOtaPackageName();
        	mUri = new URI("http://" + getRemoteHost() + "/OtaUpdater/"+mOtaPackageName);
//            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        mFileName = startIntent.getStringExtra("OtaPackageName");
        homeInfo = getPackageManager().resolveActivity(
            (new Intent(Intent.ACTION_MAIN))
            .addCategory(Intent.CATEGORY_HOME), 0);
        mNotifyManager = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        mNotify = new Notification(R.drawable.ota_update,
            getString(R.string.app_name), System.currentTimeMillis());
        mNotify.contentView = new RemoteViews(getPackageName(),
            R.layout.download_notify);
        mNotify.contentView.setProgressBar(R.id.pb_download, 100, 0, false);
        Intent intent = new Intent(this, PackageDownloadActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, 0);
        mNotify.contentIntent = pending;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(1, WAKELOCK_KEY);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_horizontal);
        mBtnControl = (Button) findViewById(R.id.btn_control);
        mBtnCancel = (Button) findViewById(R.id.button_cancel);
        mBtnControl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mState == STATE_READY || mState == STATE_ERROR) {
//                    mTask = new FileDownloadTask(mHttpClient, mUri, "/flash", mFileName, 3);
                    mTask = new FileDownloadTask(mHttpClient, mUri, "/sdcard", mFileName, 3);
                    mTask.setProgressHandler(mProgressHandler);
                    mTask.start();
                    mBtnControl.setText(getString(R.string.starting));
                    mBtnControl.setClickable(false);
                    mBtnControl.setFocusable(false);
                } else if (mState == STATE_DOWNLOADING) {
                    mTask.stopDownload();
                    mBtnControl.setText(getString(R.string.stoping));
                    mBtnControl.setClickable(false);
                    mBtnControl.setFocusable(false);
                }
            }
        });
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        mProgressBar.setIndeterminate(false);
        mProgressBar.setProgress(0);
        mProgressHandler = new ProgressHandler();
        mHttpClient = CustomerHttpClient.getHttpClient();
//        mTask = new FileDownloadTask(mHttpClient, mUri, "/flash", mFileName, 3);
        mTask = new FileDownloadTask(mHttpClient, mUri, "/sdcard", mFileName, 3);
        mTask.setProgressHandler(mProgressHandler);
        mTask.start();
        mBtnControl.setText(getString(R.string.starting));
        mBtnControl.setClickable(false);
        mBtnControl.setFocusable(false);
    }

    private String getOtaPackageName() {
    	BufferedReader reader = null;
		StringBuilder builder = null;
		File output = new File("/sdcard", "checksum");
		try {
			reader = new BufferedReader(new FileReader(output));
			builder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}
		}
		String packageName = StrParsing(builder.toString(), 2);
		return packageName;
	}

    private String StrParsing(String token, int x) {
		int n = 0;
		final String[] retval;
		final StringTokenizer strtok = new StringTokenizer(token, " ");
		retval = new String[4];
		while (strtok.hasMoreElements()) {
			++n;
			retval[n] = strtok.nextToken();
		}
		return retval[x].toString();
	}

	private String getRemoteHost() {
		// TODO Auto-generated method stub
    	String remoteHost = SystemProperties.get("ro.product.ota.host");
		return remoteHost;
	}

	private ProgressHandler mProgressHandler;
    @SuppressLint("HandlerLeak")
	private class ProgressHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case FileDownloadTask.PROGRESS_UPDATE:
                int percent = msg.getData().getInt("percent", 0);
                Log.d(TAG, "percent = " + percent);
                mProgressBar.setProgress(percent);
                setNotificationProgress(percent);
                showNotification();
                break;
            case FileDownloadTask.PROGRESS_DOWNLOAD_COMPLETE:
                mState = STATE_READY;
                mBtnControl.setText(getString(R.string.start));
                mBtnControl.setClickable(true);
                mBtnControl.setFocusable(true);
                Intent intent = new Intent();
                intent.setClass(mContext, UpdateAndRebootActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                intent.putExtra(RKUpdateService.EXTRA_IMAGE_PATH, "/flash/" + mFileName);
                intent.putExtra(RKUpdateService.EXTRA_IMAGE_PATH, "/sdcard/" + mFileName);
                startActivity(intent);
                finish();
                break;
            case FileDownloadTask.PROGRESS_START_COMPLETE:
                mState = STATE_DOWNLOADING;
                mBtnControl.setText(getString(R.string.pause));
                mBtnControl.setClickable(true);
                mBtnControl.setFocusable(true);
                showNotification();
                mWakeLock.acquire();
                break;
            case FileDownloadTask.PROGRESS_STOP_COMPLETE:
                int err = msg.getData().getInt("err", FileDownloadTask.ERR_NOERR);
                if (err == FileDownloadTask.ERR_CONNECT_TIMEOUT) {
                } else if (err == FileDownloadTask.ERR_FILELENGTH_NOMATCH) {
                } else if (err == FileDownloadTask.ERR_NOT_EXISTS) {
                } else if (err == FileDownloadTask.ERR_REQUEST_STOP) {
                }
                mState = STATE_ERROR;
                mBtnControl.setText(getString(R.string.retry));
                mBtnControl.setClickable(true);
                mBtnControl.setFocusable(true);
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                break;
            }
        }
    }

    private void showNotification() {
        mNotifyManager.notify(notification_id, mNotify);
    }

    private void clearNotification() {
        mNotifyManager.cancel(notification_id);
    }

    private void setNotificationProgress(int percent) {
        mNotify.contentView.setProgressBar(R.id.pb_download, 100, percent, false);
    }

    @SuppressLint("Wakelock")
	protected void onDestroy() {
        Log.d(TAG, "ondestroy");
        if (mTask != null) {
            mTask.stopDownload();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        clearNotification();
        if (mBinder != null) {
            mBinder.unLockWorkHandler();
        }
        mContext.unbindService(mConnection);
        super.onDestroy();
    }

    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    protected void onRestart() {
        Log.d(TAG, "onRestart");
        super.onRestart();
    }

    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            ActivityInfo ai = homeInfo.activityInfo;
            Intent startIntent = new Intent(Intent.ACTION_MAIN);
            startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            startIntent.setComponent(new ComponentName(ai.packageName, ai.name));
            startActivitySafely(startIntent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    void startActivitySafely(android.content.Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
        } catch (SecurityException e) {
        }
    }
}