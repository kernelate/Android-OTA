package android.rockchip.update.service;
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.os.PowerManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

/**
 * RecoverySystem contains methods for interacting with the Android recovery
 * system (the separate partition that can be used to install system updates,
 * wipe user data, etc.)
 */
public class RecoverySystem {
	private static final String TAG = "RecoverySystem";

	/**
	 * Default location of zip file containing public keys (X509 certs)
	 * authorized to sign OTA updates.
	 */
	private static final File DEFAULT_KEYSTORE = new File("/system/etc/security/otacerts.zip");

	/** Send progress to listeners no more often than this (in ms). */
	private static final long PUBLISH_PROGRESS_INTERVAL_MS = 500;

	/** Used to communicate with recovery. See bootable/recovery/recovery.c. */
	private static File RECOVERY_DIR = new File("/cache/recovery");
	private static File UPDATE_FLAG_FILE = new File(RECOVERY_DIR, "last_flag");
	private static File COMMAND_FILE = new File(RECOVERY_DIR, "command");
	private static File LOG_FILE = new File(RECOVERY_DIR, "log");
	private static String LAST_PREFIX = "last_";
	private static File CHECKSUM_FILE = new File("/sdcard","checksum");

	// Length limits for reading files.
	private static int LOG_FILE_MAX_LENGTH = 64 * 1024;

	private static final int TCP_PORT = 5001;

	/**
	 * Interface definition for a callback to be invoked regularly as
	 * verification proceeds.
	 */
	public interface ProgressListener {
		/**
		 * Called periodically as the verification progresses.
		 *
		 * @param progress
		 *            the approximate percentage of the verification that has
		 *            been completed, ranging from 0 to 100 (inclusive).
		 */
		public void onProgress(int progress);
	}

	/** @return the set of certs that can be used to sign an OTA package. */
	private static HashSet<Certificate> getTrustedCerts(File keystore) throws IOException, GeneralSecurityException {
		HashSet<Certificate> trusted = new HashSet();
		if (keystore == null) {
			keystore = DEFAULT_KEYSTORE;
		}
		ZipFile zip = new ZipFile(keystore);
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				trusted.add(cf.generateCertificate(zip.getInputStream(entry)));
			}
		} finally {
			zip.close();
		}
		return trusted;
	}

	/**
	 * Verify the cryptographic signature of a system update package before
	 * installing it. Note that the package is also verified separately by the
	 * installer once the device is rebooted into the recovery system. This
	 * function will return only if the package was successfully verified;
	 * otherwise it will throw an exception.
	 *
	 * Verification of a package can take significant time, so this function
	 * should not be called from a UI thread. Interrupting the thread while this
	 * function is in progress will result in a SecurityException being thrown
	 * (and the thread's interrupt flag will be cleared).
	 * 
	 * @param imagePath
	 *
	 * @param packageFile
	 *            the package to be verified
	 * @param listener
	 *            an object to receive periodic progress updates as verification
	 *            proceeds. May be null.
	 * @param deviceCertsZipFile
	 *            the zip file of certificates whose public keys we will accept.
	 *            Verification succeeds if the package is signed by the private
	 *            key corresponding to any public key in this file. May be null
	 *            to use the system default file (currently
	 *            "/system/etc/security/otacerts.zip").
	 *
	 * @throws IOException
	 *             if there were any errors reading the package or certs files.
	 * @throws GeneralSecurityException
	 *             if verification failed
	 */
	public static boolean verifyPackage(String imagePath) {
		// TODO:
		String mFileChksum;
		
		if(imagePath.equals(null))
			return false;
		
		mFileChksum = getFileChecksum(imagePath);
		
		BufferedReader reader = null;
		StringBuilder builder = null;
		File output = new File("/sdcard", "checksum");
		
		if(!output.exists())
			return false;
		
		try {
			reader = new BufferedReader(new FileReader(output));
			builder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		String checksum = StrParsing(builder.toString(), 1);
		
		if(!checksum.equals(mFileChksum)){
			return false;
		}
		
		return true;
		
	}

	private static String StrParsing(String token, int i) {
		int n = 0;
		final String[] retval;
		final StringTokenizer strtok = new StringTokenizer(token, " ");
		retval = new String[4];
		while (strtok.hasMoreElements()) {
			++n;
			retval[n] = strtok.nextToken();
		}
		return retval[i].toString();
	}

	private static String getFileChecksum(String imagePath) {

		String md5 = null;
		try {
			md5 = getMD5Checksum(imagePath);
			System.out.println(md5);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return md5;
	}

	private static String getMD5Checksum(String imagePath) {
		byte[] b = createChecksum(imagePath);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	private static byte[] createChecksum(String imagePath) {
		InputStream fis = null;
		byte[] buffer = new byte[1024];
		MessageDigest complete = null;
		int numRead = 0;
		try {
			fis = new FileInputStream(imagePath);
			complete = MessageDigest.getInstance("MD5");
			do {
				try {
					numRead = fis.read(buffer);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return complete.digest();
	}

	/**
	 * Reboots the device in order to install the given update package. Requires
	 * the {@link android.Manifest.permission#REBOOT} permission.
	 *
	 * @param context
	 *            the Context to use
	 * @param packageFile
	 *            the update package to install. Currently must be on the /cache
	 *            or /data partitions.
	 *
	 * @throws IOException
	 *             if writing the recovery command file fails, or if the reboot
	 *             itself fails.
	 */
	public static void installPackage(Context context, File packageFile) throws IOException {
		String filename = packageFile.getPath();

		Log.w(TAG, "!!! REBOOTING TO INSTALL " + filename + " !!!");
		String arg = "--update_package=" + filename;
		writeFlagCommand(filename);
		bootCommand(context, arg);
	}

	public static void installRKimage(Context context, String imagePath) throws IOException {

		Log.w(TAG, "!!! REBOOTING TO INSTALL rkimage " + imagePath + " !!!");
		String arg = "--update_rkimage=" + imagePath;
		writeFlagCommand(imagePath);
		bootCommand(context, arg);
	}

	public static String readFlagCommand() {
		Log.d(TAG, "readFlagCommand()");
		if (UPDATE_FLAG_FILE.exists()) {
			char[] buf = new char[128];
			int readCount = 0;
			FileReader reader = null;
			try {
				reader = new FileReader(UPDATE_FLAG_FILE);
				readCount = reader.read(buf, 0, buf.length);
				Log.d(TAG, "readCount = " + readCount + " buf.length = " + buf.length);
			} catch (IOException e) {
				Log.e(TAG, "can not read /cache/recovery/flag!");
			} finally {
				UPDATE_FLAG_FILE.delete();
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			StringBuilder sBuilder = new StringBuilder();
			for (int i = 0; i < readCount; i++) {
				if (buf[i] == 0)
					break;
				sBuilder.append(buf[i]);
			}
			return sBuilder.toString();
		}
		return null;
	}

	public static void writeFlagCommand(String path) throws IOException {

		String cmd;
		RECOVERY_DIR.mkdirs();
		UPDATE_FLAG_FILE.delete();
		
		if(path.equals(null)){
			Log.e(TAG, "cant create update_flag file!");
			return;
		}
		
		cmd = "PLATFORM:CMD_CREATE_FILE:"+path+":"+UPDATE_FLAG_FILE+":END";
		Log.w(TAG,cmd);
		PlatformServiceTask ps = new PlatformServiceTask(cmd);
		ps.execute();
//		FileWriter writer = new FileWriter(UPDATE_FLAG_FILE);
//		try {
//			writer.write("updating$path=" + path);
//		} finally {
//			writer.close();
//		}

//		try  {
//			
//			OutputStream fop = new FileOutputStream(UPDATE_FLAG_FILE);
//			
//			// if file doesn't exists, then create it
//			if (!UPDATE_FLAG_FILE.exists()) {
//				UPDATE_FLAG_FILE.createNewFile();
//			}
//
//			// get the content in bytes
//			byte[] contentInBytes = path.getBytes();
//
//			fop.write(contentInBytes);
//			fop.flush();
//			fop.close();
//
//			System.out.println("Done");
//
//		} catch (IOException e) {
//			System.out.println("error");
//			e.printStackTrace();
//			return;
//		}
		
	}
	
	/**
	 * Reboot into the recovery system with the supplied argument.
	 * 
	 * @param arg
	 *            to pass to the recovery utility.
	 * @throws IOException
	 *             if something goes wrong.
	 */
	private static void bootCommand(Context context, String arg) throws IOException {
		RECOVERY_DIR.mkdirs(); // In case we need it
		COMMAND_FILE.delete(); // In case it's not writable
		LOG_FILE.delete();
		
		String cmd;
		
//		FileWriter command = new FileWriter(COMMAND_FILE);
//		try {
//			command.write(arg);
//			command.write("\n");
//		} finally {
//			command.close();
//		}
//		
//		if(arg.equals(null)){
//			Log.e(TAG, "cant create command file!");
//			return;
//		}
		cmd = "PLATFORM:CMD_CREATE_FILE:"+arg+":"+COMMAND_FILE+":END";
		Log.w(TAG,cmd);
		PlatformServiceTask ps = new PlatformServiceTask(cmd);
		ps.execute();
		
//		cmd = "PLATFORM:CMD_RECOVERY:END";
//		ps = new PlatformServiceTask(cmd);
//		ps.execute();

		 // Having written the command file, go ahead and reboot
		 PowerManager pm = (PowerManager)
		 context.getSystemService(Context.POWER_SERVICE);
		 pm.reboot("recovery");
		
		 throw new IOException("Reboot failed (no permissions?)");
	}
}
