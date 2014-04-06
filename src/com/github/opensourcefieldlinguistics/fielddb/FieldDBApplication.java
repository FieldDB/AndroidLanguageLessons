package com.github.opensourcefieldlinguistics.fielddb;

/* https://github.com/ACRA/acralyzer/wiki/setup */
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.acra.ACRA;
import org.acra.ACRAConfiguration;
import org.acra.annotation.ReportsCrashes;

import ca.ilanguage.oprime.database.User;
import ca.ilanguage.oprime.database.UserContentProvider.UserTable;

import com.github.opensourcefieldlinguistics.fielddb.database.FieldDBUserContentProvider;
import com.github.opensourcefieldlinguistics.fielddb.lessons.Config;
import com.github.opensourcefieldlinguistics.fielddb.lessons.R;
import com.github.opensourcefieldlinguistics.fielddb.service.DownloadDatumsService;
import com.github.opensourcefieldlinguistics.fielddb.service.RegisterUserService;

import android.app.Application;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

@ReportsCrashes(formKey = "", formUri = "", reportType = org.acra.sender.HttpSender.Type.JSON, httpMethod = org.acra.sender.HttpSender.Method.PUT, formUriBasicAuthLogin = "see_private_constants", formUriBasicAuthPassword = "see_private_constants")
public class FieldDBApplication extends Application {
	User mUser;

	@Override
	public final void onCreate() {
		super.onCreate();

		(new File(Config.DEFAULT_OUTPUT_DIRECTORY)).mkdirs();

		ACRAConfiguration config = ACRA.getNewDefaultConfig(this);
		config.setFormUri(Config.ACRA_SERVER_URL);
		config.setFormUriBasicAuthLogin(Config.ACRA_USER);
		config.setFormUriBasicAuthPassword(Config.ACRA_PASS);

		/* https://github.com/OpenSourceFieldlinguistics/FieldDB/issues/1435 */
		boolean doesAcraSupportKeystoresWorkaroundForSNIMissingVirtualhosts = false;
		if (doesAcraSupportKeystoresWorkaroundForSNIMissingVirtualhosts) {

			// Get an instance of the Bouncy Castle KeyStore format
			KeyStore trusted;
			try {
				trusted = KeyStore.getInstance("BKS");
				// Get the raw resource, which contains the keystore with
				// your trusted certificates (root and any intermediate certs)
				InputStream in = getApplicationContext().getResources()
						.openRawResource(R.raw.sslkeystore);
				try {
					// Initialize the keystore with the provided trusted
					// certificates
					// Also provide the password of the keystore
					trusted.load(in, Config.KEYSTORE_PASS.toCharArray());
					// TODO waiting for https://github.com/ACRA/acra/pull/132
					// config.setKeyStore(trusted);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (CertificateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} catch (KeyStoreException e1) {
				e1.printStackTrace();
			}

		} else {
			// TODO waiting for https://github.com/ACRA/acra/pull/132
			config.setDisableSSLCertValidation(true);
		}

		ACRA.setConfig(config);

		ACRA.init(this);

		// Get the user from the db
		String[] userProjection = { UserTable.COLUMN_ID, UserTable.COLUMN_REV,
				UserTable.COLUMN_USERNAME, UserTable.COLUMN_FIRSTNAME,
				UserTable.COLUMN_LASTNAME, UserTable.COLUMN_EMAIL,
				UserTable.COLUMN_GRAVATAR, UserTable.COLUMN_AFFILIATION,
				UserTable.COLUMN_RESEARCH_INTEREST,
				UserTable.COLUMN_DESCRIPTION, UserTable.COLUMN_SUBTITLE };
		CursorLoader cursorLoader = new CursorLoader(getApplicationContext(),
				FieldDBUserContentProvider.CONTENT_URI, userProjection, null,
				null, null);
		Cursor cursor = cursorLoader.loadInBackground();
		cursor.moveToFirst();
		String _id = "";
		if (cursor.getCount() > 0) {
			_id = cursor.getString(cursor
					.getColumnIndexOrThrow(UserTable.COLUMN_ID));
			String _rev = cursor.getString(cursor
					.getColumnIndexOrThrow(UserTable.COLUMN_REV));
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
			String actualJSON = "";
			mUser = new User(_id, _rev, username, firstname, lastname, email,
					gravatar, affiliation, researchInterest, description,
					subtitle, null, actualJSON);
			ACRA.getErrorReporter().putCustomData("username", username);
		} else {
			Log.e(Config.TAG,
					"There is no user... this is a problme the app wont work.");
			ACRA.getErrorReporter().putCustomData("username", "unknown");
		}
		Log.d(Config.TAG, cursor.getString(cursor
				.getColumnIndexOrThrow(UserTable.COLUMN_USERNAME)));
		cursor.close();

		/*
		 * If we are in debug mode, or the user is connected to wifi, download
		 * updates for samples and also register the user if they weren't
		 * registered before
		 */
		ConnectivityManager connManager = (ConnectivityManager) getApplicationContext()
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (wifi.isConnected()) {
			Intent updateSamples = new Intent(getApplicationContext(),
					DownloadDatumsService.class);
			getApplicationContext().startService(updateSamples);

		}
		if (mUser.get_rev() == null || "".equals(mUser.get_rev())) {
			Intent registerUser = new Intent(getApplicationContext(),
					RegisterUserService.class);
			registerUser.setData(Uri
					.parse(FieldDBUserContentProvider.CONTENT_URI + "/" + _id));
			getApplicationContext().startService(registerUser);
		}

	}
}
