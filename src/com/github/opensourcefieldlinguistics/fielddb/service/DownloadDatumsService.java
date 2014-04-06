package com.github.opensourcefieldlinguistics.fielddb.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.acra.ACRA;

import ca.ilanguage.oprime.datacollection.NotifyingIntentService;

import com.github.opensourcefieldlinguistics.fielddb.database.AudioVideoContentProvider;
import com.github.opensourcefieldlinguistics.fielddb.database.DatumContentProvider;
import com.github.opensourcefieldlinguistics.fielddb.database.AudioVideoContentProvider.AudioVideoTable;
import com.github.opensourcefieldlinguistics.fielddb.database.DatumContentProvider.DatumTable;
import com.github.opensourcefieldlinguistics.fielddb.lessons.Config;
import com.github.opensourcefieldlinguistics.fielddb.lessons.R;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class DownloadDatumsService extends NotifyingIntentService {
	String datumTagToDownload;
	String urlStringSampleDataDownload;
	JsonArray resultsJSON;
	ArrayList<String> additionalDownloads;

	public DownloadDatumsService(String name) {
		super(name);
	}

	public DownloadDatumsService() {
		super("DownloadDatumsService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		this.D = Config.D;
		this.statusMessage = "Downloading samples "
				+ Config.USER_FRIENDLY_DATA_NAME;
		this.tryAgain = intent;
		this.keystoreResourceId = R.raw.sslkeystore;
		if (Config.D) {
			Log.d(Config.TAG, "Inside DownloadDatumsService intent");
		}

		this.datumTagToDownload = "SampleData";
		this.urlStringSampleDataDownload = Config.DEFAULT_SAMPLE_DATA_URL
				+ "?key=%22" + datumTagToDownload + "%22";
		if (Config.D) {
			Log.d(Config.TAG, this.urlStringSampleDataDownload);
		}

		ACRA.getErrorReporter().putCustomData("action",
				"downloadDatums:::" + datumTagToDownload);
		ACRA.getErrorReporter().putCustomData("urlString",
				this.urlStringSampleDataDownload);
		super.onHandleIntent(intent);

		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		this.getCouchCookie(Config.DEFAULT_PUBLIC_USERNAME,
				Config.DEFAULT_PUBLIC_USER_PASS, Config.DEFAULT_DATA_LOGIN);
		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		this.getSampleData();
		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		this.processCouchDBMapResponse();
		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		/* Success: remove the notification */
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
				.cancel(this.notificationId);
		ACRA.getErrorReporter().putCustomData("action",
				"downloadDatums:::" + datumTagToDownload);
		ACRA.getErrorReporter().putCustomData("urlString",
				this.urlStringSampleDataDownload);
		ACRA.getErrorReporter().handleException(
				new Exception("*** Downloaded data sucessfully ***"));
	}

	public void getSampleData() {
		URL url;
		try {
			url = new URL(this.urlStringSampleDataDownload);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem determining which server to contact, please report this error.";
			return;
		}
		this.statusMessage = "Contacting server...";
		this.notifyUser(this.statusMessage, this.noti, notificationId, false);
		HttpURLConnection urlConnection;
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.connect();
		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem contacting the server to download sample data.";
			return;
		}

		String JSONResponse = this.processResponse(url, urlConnection);
		if (!"".equals(this.userFriendlyErrorMessage)) {
			return;
		}
		if (JSONResponse == null) {
			this.userFriendlyErrorMessage = "Unknown error reading sample data from server";
			return;
		}
		JsonObject json = (JsonObject) DownloadDatumsService.jsonParser
				.parse(JSONResponse);
		this.resultsJSON = json.getAsJsonArray("rows");
		return;
	}

	public void processCouchDBMapResponse() {
		if (this.resultsJSON == null || this.resultsJSON.size() == 0) {
			this.userFriendlyErrorMessage = "The sample data was empty, please report this.";
			return;
		}
		this.statusMessage = "Processing response...";
		this.notifyUser(this.statusMessage, this.noti, notificationId, false);

		JsonObject datumJson;
		String id = "";
		Uri uri;
		String[] datumProjection = { DatumTable.COLUMN_ID };
		Cursor cursor;
		String mediaFilesAsString = "";
		ContentValues datumAsValues;
		additionalDownloads = new ArrayList<String>();
		for (int row = 0; row < this.resultsJSON.size(); row++) {
			datumJson = (JsonObject) this.resultsJSON.get(row);
			datumJson = datumJson.getAsJsonObject("value");
			id = datumJson.get("_id").getAsString();
			uri = Uri.withAppendedPath(DatumContentProvider.CONTENT_URI, id);
			cursor = getContentResolver().query(uri, datumProjection, null,
					null, null);

			// TODO instead, update it, without losing info
			if (cursor == null || cursor.getCount() <= 0) {
				/* save it */
				try {
					datumAsValues = new ContentValues();
					datumAsValues.put(DatumTable.COLUMN_ID, id);
					datumAsValues.put(DatumTable.COLUMN_REV,
							datumJson.get("_rev").getAsString());
					datumAsValues.put(DatumTable.COLUMN_CREATED_AT, datumJson
							.get("created_at").getAsString());
					datumAsValues.put(DatumTable.COLUMN_UPDATED_AT, datumJson
							.get("updated_at").getAsString());
					datumAsValues.put(
							DatumTable.COLUMN_APP_VERSIONS_WHEN_MODIFIED,
							datumJson.get("appVersionsWhenModified")
									.getAsString());
					datumAsValues.put(DatumTable.COLUMN_RELATED,
							datumJson.get("related").getAsString());

					datumAsValues.put(DatumTable.COLUMN_UTTERANCE, datumJson
							.get("utterance").getAsString());
					datumAsValues.put(DatumTable.COLUMN_MORPHEMES, datumJson
							.get("morphemes").getAsString());
					datumAsValues.put(DatumTable.COLUMN_GLOSS,
							datumJson.get("gloss").getAsString());
					datumAsValues.put(DatumTable.COLUMN_TRANSLATION, datumJson
							.get("translation").getAsString());
					datumAsValues.put(DatumTable.COLUMN_ORTHOGRAPHY, datumJson
							.get("orthography").getAsString());
					datumAsValues.put(DatumTable.COLUMN_CONTEXT,
							datumJson.get("context").getAsString());
					datumAsValues.put(DatumTable.COLUMN_TAGS,
							datumJson.get("tags").getAsString());
					datumAsValues.put(DatumTable.COLUMN_VALIDATION_STATUS,
							datumJson.get("validationStatus").getAsString());
					datumAsValues.put(DatumTable.COLUMN_ENTERED_BY_USER,
							datumJson.get("enteredByUser").getAsString());
					datumAsValues.put(DatumTable.COLUMN_MODIFIED_BY_USER,
							datumJson.get("modifiedByUser").getAsString());
					datumAsValues.put(DatumTable.COLUMN_COMMENTS, datumJson
							.get("comments").getAsString());

					mediaFilesAsString = datumJson.get("images").getAsString();
					mediaFilesAsString = this
							.addAdditionalDownloads(mediaFilesAsString);
					datumAsValues.put(DatumTable.COLUMN_IMAGE_FILES,
							mediaFilesAsString);

					mediaFilesAsString = datumJson.get("audioVideo")
							.getAsString();
					mediaFilesAsString = this
							.addAdditionalDownloads(mediaFilesAsString);
					datumAsValues.put(DatumTable.COLUMN_AUDIO_VIDEO_FILES,
							mediaFilesAsString);

					uri = getContentResolver().insert(
							DatumContentProvider.CONTENT_URI, datumAsValues);
				} catch (Exception e) {
					Log.d(Config.TAG,
							"Failed to insert this sample most likely something was missing from the server...");
					e.printStackTrace();
				}
			}
		}

		if (this.additionalDownloads.size() > 0) {
			Log.d(Config.TAG,
					"TODO download the image and audio files through a filter that makes them smaller... ");
		}
		// ACRA.getErrorReporter().handleException(
		// new Exception("*** Download Data Completed ***"));
		return;
	}

	public void downloadMediaFile(String mediaFileUrl) {
		if (mediaFileUrl == null || "".equals(mediaFileUrl)) {
			Log.d(Config.TAG,
					"Not re-requesting download of media file, it is a blank string");
			return;
		}
		/*
		 * TODO sanitize url and filename and size or something... to ensure its
		 * not dangerous
		 */
		String filename = Uri.parse(mediaFileUrl).getLastPathSegment();

		if ((new File(Config.DEFAULT_OUTPUT_DIRECTORY + "/" + filename))
				.exists()) {
			Log.d(Config.TAG, "Not re-requesting download of " + mediaFileUrl
					+ " a file with this name already exists...");
			return;
		}

		URL url;
		try {
			url = new URL(mediaFileUrl);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem determining which server to contact for media data, please report this error."
					+ mediaFileUrl;
			return;
		}
		this.statusMessage = "Contacting server...";
		this.notifyUser(this.statusMessage, this.noti, notificationId, false);
		HttpURLConnection urlConnection;
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.connect();
		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem contacting the server to download media data.";
			return;
		}
		if (!url.getHost().equals(urlConnection.getURL().getHost())) {
			Log.d(Config.TAG,
					"We were redirected! Kick the user out to the browser to sign on?");
		}

		/* Open the input or error stream */
		int status;
		try {
			status = urlConnection.getResponseCode();
		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem getting server resonse code for media file.";
			return;
		}
		if (Config.D) {
			Log.d(Config.TAG, "Server status code " + status);
		}
		this.statusMessage = "Downloading...";
		this.notifyUser(this.statusMessage, this.noti, notificationId, false);
		BufferedInputStream reader;
		try {
			if (status < 400 && urlConnection.getInputStream() != null) {
				reader = new BufferedInputStream(urlConnection.getInputStream());
				byte[] buffer = new byte[4096];
				int n = -1;
				OutputStream output = new FileOutputStream(
						Config.DEFAULT_OUTPUT_DIRECTORY + "/" + filename);
				while ((n = reader.read(buffer)) != -1) {
					if (n > 0) {
						output.write(buffer, 0, n);
					}
				}
				output.close();
				this.statusMessage = "Downloaded " + filename;
				ACRA.getErrorReporter().putCustomData("action",
						"downloadMedia:::" + filename);
				ACRA.getErrorReporter()
						.putCustomData("urlString", mediaFileUrl);
				ACRA.getErrorReporter().handleException(
						new Exception(
								"*** Downloaded media file sucessfully ***"));
			} else {
				this.userFriendlyErrorMessage = "Server replied " + status;
			}

		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem writing to the server connection.";
			return;
		}

		return;
	}

	public Uri insertMediaFileInDB(String url) {
		String filename = Uri.parse(url).getLastPathSegment();
		String[] audioVideoProjection = { AudioVideoTable.COLUMN_FILENAME };

		Uri uri = Uri.withAppendedPath(AudioVideoContentProvider.CONTENT_URI,
				filename);
		Cursor cursor = getContentResolver().query(uri, audioVideoProjection,
				null, null, null);

		// TODO instead, update it, without losing info
		if (cursor == null || cursor.getCount() <= 0) {
			ContentValues mediaAsValues = new ContentValues();
			mediaAsValues.put(AudioVideoTable.COLUMN_ID, filename);
			mediaAsValues.put(AudioVideoTable.COLUMN_FILENAME, filename);
			mediaAsValues.put(AudioVideoTable.COLUMN_URL, url);
			uri = getContentResolver().insert(
					AudioVideoContentProvider.CONTENT_URI, mediaAsValues);
		}
		return uri;
	}

	public String addAdditionalDownloads(String commadelimitedUrls) {
		String[] urls = commadelimitedUrls.split(",");
		String filenames = "";
		for (String url : urls) {
			url = url.replaceAll("SERVER_URL", Config.DEFAULT_DATA_SERVER_URL);
			this.additionalDownloads.add(url);
			this.downloadMediaFile(url);
			this.insertMediaFileInDB(url);
			if (!"".equals(filenames)) {
				filenames = filenames + ",";
			}
			filenames = filenames + Uri.parse(url).getLastPathSegment();
		}
		return filenames;
	}

}
