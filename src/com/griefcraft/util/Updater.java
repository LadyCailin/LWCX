/*
 * Copyright 2011 Tyler Blair. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package com.griefcraft.util;

import com.griefcraft.lwc.LWC;
import com.griefcraft.sql.Database;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.io.IOUtils;
import org.bukkit.Bukkit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class Updater {

	/**
	 * URL to the base update site
	 */
	public final static String UPDATE_SITE = "http://192.210.174.254";

	/**
	 * Location of the plugin on the website
	 */
	public final static String PLUGIN_LOCATION = "/lwc/";

	/**
	 * The folder where libraries are stored
	 */
	public final static String DEST_LIBRARY_FOLDER = "plugins/LWC/lib/";

	/**
	 * The queue of files that need to be downloaded
	 */
	private final Queue<UpdaterFile> fileQueue = new ConcurrentLinkedQueue<UpdaterFile>();

	@SuppressWarnings("deprecation")
	public void init() {
		// verify we have local files (e.g sqlite.jar, etc)
		verifyFiles();
		downloadFiles();

		final LWC lwc = LWC.getInstance();
		if (Bukkit.getPluginManager().getPlugin("SpigotUpdater") != null) {
			if (lwc.getConfiguration().getBoolean("core.updateNotifier", true)) {
				lwc.getPlugin().getServer().getScheduler().scheduleAsyncDelayedTask(lwc.getPlugin(), new Runnable() {
					public void run() {
						Object[] updates = Updater.getLastUpdate();
						if (updates.length == 2) {
							System.out.println("�6[�eEntityLWC�6] New update avaible:");
							System.out.println("�6New version: �e" + updates[0]);
							System.out.println(
									"�6Your version: �e" + LWC.getInstance().getPlugin().getDescription().getVersion());
							System.out.println("�6What's new: �e" + updates[1]);
						}
					}

				});
			}
		} else {
			System.out.println("[LWC] SpigotUpdater not enabled. If you want to use the updater, please install this:");
			System.out.println("[LWC] https://www.spigotmc.org/resources/api-spigotupdater.7563/");
		}
	}

	/**
	 * Verify all required files exist
	 */
	private void verifyFiles() {
		// SQLite libraries
		if (Database.DefaultType == Database.Type.SQLite) {
			// sqlite.jar
			this.verifyFile(
					new UpdaterFile(DEST_LIBRARY_FOLDER + "sqlite.jar", UPDATE_SITE + "/shared/lib/sqlite.jar"));

			String nativeLibraryPath = getFullNativeLibraryPath();

			if (nativeLibraryPath != null) {
				// Native library
				this.verifyFile(new UpdaterFile(getFullNativeLibraryPath(),
						UPDATE_SITE + "/shared/lib/" + nativeLibraryPath.replaceAll(DEST_LIBRARY_FOLDER, "")));
				System.out
						.println(UPDATE_SITE + "/shared/lib/" + nativeLibraryPath.replaceAll(DEST_LIBRARY_FOLDER, ""));
			} else {
				// XXX backwards compat:- nuke any old Linux binaries so that
				// SQLite does not load them and then crash the JVM
				File file = new File(DEST_LIBRARY_FOLDER + "native/Linux/amd64/libsqlitejdbc.so");

				if (file.exists()) {
					file.delete();
				}
			}
		}
	}

	/**
	 * Verify a file and if it does not exist, download it
	 *
	 * @param updaterFile
	 * @return true if the file was queued to be downloaded
	 */
	private boolean verifyFile(UpdaterFile updaterFile) {
		if (updaterFile == null) {
			return false;
		}

		File file = new File(updaterFile.getLocalLocation());

		// Does it exist on the FS?
		if (file.exists()) {
			// So it does!
			return false;
		}

		// It does not exist ..
		fileQueue.offer(updaterFile);
		return true;
	}

	/**
	 * @return the full path to the native library for sqlite. null if none
	 *         exists
	 */
	public String getFullNativeLibraryPath() {
		String osFolder = getOSSpecificFolder();
		String osFileName = getOSSpecificFileName();

		if (osFolder == null || osFileName == null) {
			return null;
		}

		return osFolder + osFileName;
	}

	/**
	 * @return the os/arch specific file name for sqlite's native library. null
	 *         if none exists
	 */
	public String getOSSpecificFileName() {
		String osname = System.getProperty("os.name").toLowerCase();

		if (osname.contains("windows")) {
			return "sqlitejdbc.dll";
		} else if (osname.contains("mac")) {
			return "libsqlitejdbc.jnilib";
		} else if (osname.contains("bsd")) {
			return null;
		} else { /* We assume linux */
			return "libsqlitejdbc.so";
		}
	}

	/**
	 * @return the os/arch specific folder location for SQLite's native library.
	 *         null if none exists
	 */
	public String getOSSpecificFolder() {
		String osname = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();

		if (osname.contains("windows")) {
			return DEST_LIBRARY_FOLDER + "native/Windows/" + arch + "/";
		} else if (osname.contains("mac")) {
			return DEST_LIBRARY_FOLDER + "native/Mac/" + arch + "/";
		} else if (osname.contains("bsd")) {
			return null;
		} else { /* We assume linux */
			return DEST_LIBRARY_FOLDER + "native/Linux/" + arch + "/";
		}
	}

	/**
	 * Download all the files in the queue
	 */
	public void downloadFiles() {
		synchronized (fileQueue) {
			UpdaterFile updaterFile = null;
			LWC lwc = LWC.getInstance();

			while ((updaterFile = fileQueue.poll()) != null) {
				try {
					File local = new File(updaterFile.getLocalLocation());
					String remote = updaterFile.getRemoteLocation();

					lwc.log("Downloading file " + local.getName());

					// check for LWC folder
					File folder = new File("plugins/LWC/");
					if (!folder.exists()) {
						folder.mkdir();
					}

					// check native folders
					String nativeLibraryFolder = getOSSpecificFolder();

					if (nativeLibraryFolder != null) {
						folder = new File(nativeLibraryFolder);
						if (!folder.exists()) {
							folder.mkdirs();
						}
					}

					if (local.exists()) {
						local.delete();
					}

					// create the local file
					local.createNewFile();

					// open the file
					OutputStream outputStream = new FileOutputStream(local);

					// Connect to the server
					URL url = new URL(remote);
					URLConnection connection = url.openConnection();

					InputStream inputStream = connection.getInputStream();

					// hopefully, the content length provided isn't -1
					int contentLength = connection.getContentLength();

					// Keep a running tally
					int bytesTransffered = 0;
					long lastUpdate = 0L;

					// begin transferring
					byte[] buffer = new byte[1024];
					int read;

					while ((read = inputStream.read(buffer)) > 0) {
						outputStream.write(buffer, 0, read);
						bytesTransffered += read;

						if (contentLength > 0) {
							if (System.currentTimeMillis() - lastUpdate > 500L) {
								int percentTransferred = (int) (((float) bytesTransffered / contentLength) * 100);
								lastUpdate = System.currentTimeMillis();

								// omit 100% ..
								if (percentTransferred != 100) {
									lwc.log(percentTransferred + "%");
								}
							}
						}
					}

					// ok!
					outputStream.close();
					inputStream.close();
				} catch (IOException e) {
					exceptionCaught(e);
				}
			}
		}
	}

	/**
	 * Called when an exception is caught
	 *
	 * @param e
	 */
	private void exceptionCaught(Exception e) {
		LWC lwc = LWC.getInstance();
		lwc.log("[LWC] The updater ran into a minor issue: " + e.getMessage());
		lwc.log("[LWC] This can probably be ignored.");
	}

	final static String VERSION_URL = "https://api.spiget.org/v2/resources/2162/versions?size=" + Integer.MAX_VALUE
			+ "&spiget__ua=SpigetDocs";
	final static String DESCRIPTION_URL = "https://api.spiget.org/v2/resources/2162/updates?size=" + Integer.MAX_VALUE
			+ "&spiget__ua=SpigetDocs";

	public static Object[] getLastUpdate() {
		try {
			JSONArray versionsArray = (JSONArray) JSONValue
					.parseWithException(IOUtils.toString(new URL(String.valueOf(VERSION_URL))));
			Double lastVersion = Double
					.parseDouble(((JSONObject) versionsArray.get(versionsArray.size() - 1)).get("name").toString());

			if (lastVersion > Double.parseDouble(LWC.getInstance().getPlugin().getDescription().getVersion())) {
				JSONArray updatesArray = (JSONArray) JSONValue
						.parseWithException(IOUtils.toString(new URL(String.valueOf(DESCRIPTION_URL))));
				String updateName = ((JSONObject) updatesArray.get(updatesArray.size() - 1)).get("title").toString();

				Object[] update = { lastVersion, updateName };
				return update;
			}
		} catch (Exception e) {
			return new String[0];
		}

		return new String[0];
	}
}
