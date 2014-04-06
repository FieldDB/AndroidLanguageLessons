package com.github.opensourcefieldlinguistics.fielddb.service;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import org.acra.ACRA;

import ca.ilanguage.oprime.database.UserContentProvider.UserTable;
import ca.ilanguage.oprime.datacollection.NotifyingIntentService;

import com.github.opensourcefieldlinguistics.fielddb.database.FieldDBUserContentProvider;
import com.github.opensourcefieldlinguistics.fielddb.lessons.Config;
import com.github.opensourcefieldlinguistics.fielddb.lessons.R;
import com.google.gson.JsonObject;

import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class RegisterUserService extends NotifyingIntentService {

	public RegisterUserService(String name) {
		super(name);
	}

	public RegisterUserService() {
		super("RegisterUserService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		this.D = Config.D;
		this.statusMessage = "Registering user";
		this.tryAgain = intent;
		this.keystoreResourceId = R.raw.sslkeystore;
		if (Config.D) {
			Log.d(Config.TAG, "Inside RegisterUserService intent");
		}
		ACRA.getErrorReporter().putCustomData("action", "registerUser:::");
		ACRA.getErrorReporter().putCustomData("urlString",
				Config.DEFAULT_REGISTER_USER_URL);

		super.onHandleIntent(intent);

		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		String JSONResponse = this.registerUsers(intent.getData());
		if (JSONResponse == null && "".equals(this.userFriendlyErrorMessage)) {
			this.userFriendlyErrorMessage = "Server response was missing. Please report this.";
		}
		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		processRegistrationResponse(intent.getData(), JSONResponse);
		if (!"".equals(this.userFriendlyErrorMessage)) {
			this.notifyUser(" " + this.userFriendlyErrorMessage, this.noti,
					this.notificationId, true);
			ACRA.getErrorReporter().handleException(
					new Exception(this.userFriendlyErrorMessage));
			return;
		}

		/* all is well, get their cookie set */
		// this.getCouchCookie(username, generatedPassword,
		// Config.DEFAULT_DATA_LOGIN);

		/* Success: remove the notification */
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
				.cancel(this.notificationId);
		ACRA.getErrorReporter().handleException(
				new Exception("*** Registered user ssucessfully ***"));
	}

	public String loginUser(String username, String password, String loginUrl) {
		URL url;
		HttpURLConnection urlConnection;
		try {
			url = new URL(loginUrl);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("POST");
			urlConnection
					.setRequestProperty("Content-Type", "application/json");
			urlConnection.setDoInput(true);
			urlConnection.setDoOutput(true);
			urlConnection.connect();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem determining which server to contact, please report this error.";
			return null;
		} catch (ProtocolException e) {
			this.userFriendlyErrorMessage = "Problem using POST, please report this error.";
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			this.userFriendlyErrorMessage = "Problem opening connection to server, please report this error.";
			e.printStackTrace();
			return null;
		}
		JsonObject jsonParam = new JsonObject();
		jsonParam.addProperty("username", username);
		jsonParam.addProperty("password", password);
		DataOutputStream printout;
		try {
			printout = new DataOutputStream(urlConnection.getOutputStream());
			String jsonString = jsonParam.toString();
			Log.d(Config.TAG, jsonString);
			printout.write(jsonString.getBytes());
			printout.flush();
			printout.close();
		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem writing to the server connection.";
			return null;
		}
		String JSONResponse = this.processResponse(url, urlConnection);

		return JSONResponse;
	}

	public String registerUsers(Uri uri) {
		String[] userProjection = { UserTable.COLUMN_REV,
				UserTable.COLUMN_USERNAME, UserTable.COLUMN_FIRSTNAME,
				UserTable.COLUMN_LASTNAME, UserTable.COLUMN_EMAIL,
				UserTable.COLUMN_GRAVATAR, UserTable.COLUMN_AFFILIATION,
				UserTable.COLUMN_RESEARCH_INTEREST,
				UserTable.COLUMN_DESCRIPTION, UserTable.COLUMN_SUBTITLE,
				UserTable.COLUMN_GENERATED_PASSWORD,
				UserTable.COLUMN_APP_VERSIONS_WHEN_MODIFIED };
		if (uri == null) {
			uri = FieldDBUserContentProvider.CONTENT_URI;
		}
		CursorLoader cursorLoader = new CursorLoader(getApplicationContext(),
				uri, userProjection, null, null, null);
		Cursor cursor = cursorLoader.loadInBackground();
		cursor.moveToFirst();
		if (cursor.getCount() == 0) {
			Log.e(Config.TAG,
					"There is no user... this is not supposed to happen.");
			cursor.close();
			return null;
		}

		String _rev = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_REV));
		if (_rev != null && !"".equals(_rev)) {
			/*
			 * Success:this user has been registered at some point, remove the
			 * notification
			 */
			((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
					.cancel(this.notificationId);
			return null;
		}
		String username = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_USERNAME));
		String firstname = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_FIRSTNAME));
		String lastname = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_LASTNAME));
		String email = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_EMAIL));
		String gravatar = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_GRAVATAR));
		String affiliation = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_AFFILIATION));
		String researchInterest = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_RESEARCH_INTEREST));
		String description = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_DESCRIPTION));
		String subtitle = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_SUBTITLE));
		String generatedPassword = cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_GENERATED_PASSWORD));
		String appVersionsWhenModified = cursor
				.getString(cursor
						.getColumnIndexOrThrow(UserTable.COLUMN_APP_VERSIONS_WHEN_MODIFIED));
		cursor.close();

		this.statusMessage = "Registering user " + username;
		ACRA.getErrorReporter().putCustomData("registerUser", username);
		String urlStringAuthenticationSession = Config.DEFAULT_REGISTER_USER_URL;
		URL url;
		HttpURLConnection urlConnection;
		try {
			url = new URL(urlStringAuthenticationSession);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("POST");
			urlConnection
					.setRequestProperty("Content-Type", "application/json");
			urlConnection.setDoInput(true);
			urlConnection.setDoOutput(true);
			urlConnection.connect();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem determining which server to contact, please report this error.";
			return null;
		} catch (ProtocolException e) {
			this.userFriendlyErrorMessage = "Problem using POST, please report this error.";
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			this.userFriendlyErrorMessage = "Problem opening connection to server, please report this error.";
			e.printStackTrace();
			return null;
		}

		JsonObject jsonParam = new JsonObject();
		jsonParam.addProperty("username", username);
		jsonParam.addProperty("password", generatedPassword);
		jsonParam.addProperty("firstname", firstname);
		jsonParam.addProperty("lastname", lastname);
		jsonParam.addProperty("email", email);
		jsonParam.addProperty("gravatar", gravatar);
		jsonParam.addProperty("affiliation", affiliation);
		jsonParam.addProperty("researchInterest", researchInterest);
		jsonParam.addProperty("description", description);
		jsonParam.addProperty("subtitle", subtitle);
		jsonParam.addProperty("authUrl",
				Config.DEFAULT_REGISTER_USER_URL.replace("/register", ""));
		jsonParam.addProperty("appVersionsWhenModified",
				appVersionsWhenModified);
		jsonParam.addProperty("appVersionWhenCreated", appVersionsWhenModified);
		DataOutputStream printout;
		try {
			printout = new DataOutputStream(urlConnection.getOutputStream());
			String jsonString = jsonParam.toString();
			Log.d(Config.TAG, jsonString);
			printout.write(jsonString.getBytes());
			printout.flush();
			printout.close();
		} catch (IOException e) {
			e.printStackTrace();
			this.userFriendlyErrorMessage = "Problem writing to the server connection.";
			return null;
		}
		String JSONResponse = this.processResponse(url, urlConnection);
		if (JSONResponse.contains("name already exists")) {
			JSONResponse = this.loginUser(username, generatedPassword,
					Config.DEFAULT_AUTH_LOGIN_URL);
		}
		if (!"".equals(this.userFriendlyErrorMessage)) {
			return null;
		}
		if (JSONResponse == null) {
			this.userFriendlyErrorMessage = "Unknown error reading sample data from server";
			return null;
		}
		return JSONResponse;
	}

	public int processRegistrationResponse(Uri uri, String jsonResponse) {
		JsonObject json = (JsonObject) NotifyingIntentService.jsonParser
				.parse(jsonResponse);
		if (json.has("userFriendlyErrors")) {
			this.userFriendlyErrorMessage = json.get("userFriendlyErrors")
					.getAsString();
			return 0;
		}
		if (!json.has("user")) {
			this.userFriendlyErrorMessage = "The server response is very strange, please report this.";
			return 0;
		}
		json = json.get("user").getAsJsonObject();
		ContentValues values = new ContentValues();
		try {
			values.put(UserTable.COLUMN_USERNAME, json.get("username")
					.getAsString());
			values.put(UserTable.COLUMN_ID, json.get("_id").getAsString());
			values.put(UserTable.COLUMN_REV, json.get("_rev").getAsString());
			values.put(UserTable.COLUMN_GRAVATAR, json.get("gravatar")
					.getAsString());
		} catch (Exception e) {
			this.userFriendlyErrorMessage = "Something was strange in the server response after registering your user, please report this.";
			return 0;
		}
		int countUpdated = getContentResolver().update(uri, values, null, null);
		if (uri == null) {
			this.userFriendlyErrorMessage = "Problem saving your user in the offline db";
		}
		return countUpdated;
	}
}
