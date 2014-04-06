package com.github.opensourcefieldlinguistics.fielddb.database;

import java.util.ArrayList;
import java.util.UUID;

import ca.ilanguage.oprime.database.OPrimeTable;

import com.github.opensourcefieldlinguistics.fielddb.lessons.Config;
import com.github.opensourcefieldlinguistics.fielddb.service.DownloadDatumsService;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class DatumContentProvider extends ContentProvider {
	private DatumSQLiteHelper database;
	// Used for the UriMacher
	private static final int ITEMS = 10;
	private static final int ITEM_ID = 20;

	private static final String AUTHORITY = "com.github.opensourcefieldlinguistics.fielddb."
			+ Config.DATA_IS_ABOUT_LANGUAGE_NAME.toLowerCase()
			+ "."
			+ DatumTable.TABLE_NAME;
	private static final String BASE_PATH = DatumTable.TABLE_NAME + "s";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
			+ "/" + BASE_PATH);
	public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
			+ "/" + DatumTable.TABLE_NAME + "s";
	public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
			+ "/" + DatumTable.TABLE_NAME;

	private static final UriMatcher sURIMatcher = new UriMatcher(
			UriMatcher.NO_MATCH);

	static {
		sURIMatcher.addURI(AUTHORITY, BASE_PATH, ITEMS);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/*", ITEM_ID);
	}

	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		String id;
		if (values == null) {
			values = new ContentValues();
			id = UUID.randomUUID().toString();
			values.put(DatumTable.COLUMN_ID, id);
		} else {
			id = values.getAsString(DatumTable.COLUMN_ID);
			if (id == null) {
				id = UUID.randomUUID().toString();
				values.put(DatumTable.COLUMN_ID, id);
			}
		}
		Log.d(Config.TAG, "insert " + id.toString());
		SQLiteDatabase db = database.getWritableDatabase();
		long insertedRowId = db.insert(DatumTable.TABLE_NAME, null, values);
		Log.d(Config.TAG, "insertedRowId " + insertedRowId);
		if (insertedRowId > 0) {
			uri = Uri.withAppendedPath(uri, id);
		} else {
			return null;
		}
		return uri;
	}

	@Override
	public boolean onCreate() {
		this.database = new DatumSQLiteHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		// Using SQLiteQueryBuilder instead of query() method
		SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

		// Check if the caller has requested a column which does not exists
		// checkColumns(projection);

		// Set the table
		queryBuilder.setTables(DatumTable.TABLE_NAME);

		int uriType = sURIMatcher.match(uri);
		switch (uriType) {
		case ITEMS:
			break;
		case ITEM_ID:
			// Adding the ID to the original query
			queryBuilder.appendWhere(DatumTable.COLUMN_ID + "='"
					+ uri.getLastPathSegment() + "'");
			break;
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		SQLiteDatabase db = database.getWritableDatabase();
		Cursor cursor = queryBuilder.query(db, projection, selection,
				selectionArgs, null, null, sortOrder);
		// Make sure that potential listeners are getting notified
		cursor.setNotificationUri(getContext().getContentResolver(), uri);

		return cursor;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {

		int uriType = sURIMatcher.match(uri);
		SQLiteDatabase sqlDB = database.getWritableDatabase();
		int rowsUpdated = 0;
		switch (uriType) {
		case ITEMS:
			rowsUpdated = sqlDB.update(DatumTable.TABLE_NAME, values,
					selection, selectionArgs);
			break;
		case ITEM_ID:
			String id = uri.getLastPathSegment();
			if (TextUtils.isEmpty(selection)) {
				rowsUpdated = sqlDB.update(DatumTable.TABLE_NAME, values,
						DatumTable.COLUMN_ID + "='" + id + "'", null);
			} else {
				rowsUpdated = sqlDB
						.update(DatumTable.TABLE_NAME, values,
								DatumTable.COLUMN_ID + "='" + id + "' and "
										+ selection, selectionArgs);
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown Update URI: " + uri);
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return rowsUpdated;
	}

	public class DatumSQLiteHelper extends SQLiteOpenHelper {
		private static final String DATABASE_NAME = DatumTable.TABLE_NAME
				+ ".db";
		private static final int DATABASE_VERSION = 1;

		public DatumSQLiteHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			try {
				DatumTable.setColumns();
				db.execSQL(DatumTable
						.generateCreateTableSQLStatement(DatumTable.TABLE_NAME));

				ConnectivityManager connManager = (ConnectivityManager) getContext()
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo mWifi = connManager
						.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

				if (mWifi.isConnected()) {
					// if the user has a wifi connection we can download some
					// real sample data
					Intent downloadSamples = new Intent(getContext(),
							DownloadDatumsService.class);
					getContext().startService(downloadSamples);
				} else {
					// Otherwise, insert offline data
					db.insert(DatumTable.TABLE_NAME, null,
							DatumTable.sampleData());
				}

			} catch (SQLException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			/*
			 * export user database
			 * http://stackoverflow.com/questions/805363/how
			 * -do-i-rename-a-column-in-a-sqlite-database-table The SQLite ALTER
			 * TABLE documentation can be found here. If you add new columns you
			 * can use ALTER TABLE to insert them into a live table. If you
			 * rename or remove columns you can use ALTER TABLE to rename the
			 * old table, then create the new table and then populate the new
			 * table with the contents of the old table.
			 */
			String copyTableToBackup = "ALTER TABLE " + DatumTable.TABLE_NAME
					+ " RENAME TO " + DatumTable.TABLE_NAME + "backup1;";
			db.execSQL(copyTableToBackup);

			Log.w(Config.TAG, "Upgrading database from version " + oldVersion
					+ " to " + newVersion + ", will try to copy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + DatumTable.TABLE_NAME);

			/* Re-create table using current schema, and remove the sample data */
			onCreate(db);
			try {
				String clearSampleData = "DELETE FROM " + DatumTable.TABLE_NAME
						+ ";";
				db.execSQL(clearSampleData);
			} catch (Exception e) {
				Log.w(Config.TAG,
						"Problem upgrading, unable to clear sample data." + e);
			}

			ArrayList<String> previousColumns = OPrimeTable.getBaseColumns();
			String[] knownColumns = DatumTable.version1Columns;

			/* Add other versions to this if statement */
			if (oldVersion == 1) {
				knownColumns = DatumTable.version1Columns;
			}

			/* Copy the data from previous columns over */
			for (String column : knownColumns) {
				previousColumns.add(column);
			}
			try {
				db.execSQL(DatumTable.generateUpgradeTableSQLStatement(
						DatumTable.TABLE_NAME, previousColumns));
			} catch (Exception e) {
				Log.w(Config.TAG,
						"Problem upgrading, unable to copy datum data." + e);
			}
		}
	}

	public static class DatumTable extends OPrimeTable {
		public static final String TABLE_NAME = "datum";

		public static final String COLUMN_UTTERANCE = "utterance";
		public static final String COLUMN_MORPHEMES = "morphemes";
		public static final String COLUMN_GLOSS = "gloss";
		public static final String COLUMN_TRANSLATION = "translation";
		public static final String COLUMN_ORTHOGRAPHY = "orthography";
		public static final String COLUMN_CONTEXT = "context";
		public static final String COLUMN_IMAGE_FILES = "imageFiles";
		public static final String COLUMN_AUDIO_VIDEO_FILES = "audioVideoFiles";
		public static final String COLUMN_LOCATIONS = "locations";
		public static final String COLUMN_REMINDERS = "reminders";
		public static final String COLUMN_TAGS = "tags";
		public static final String COLUMN_COMMENTS = "comments";
		public static final String COLUMN_VALIDATION_STATUS = "validationStatus";
		public static final String COLUMN_ENTERED_BY_USER = "enteredByUser";
		public static final String COLUMN_MODIFIED_BY_USER = "modifiedByUser";

		public static String[] version1Columns = { COLUMN_UTTERANCE,
				COLUMN_MORPHEMES, COLUMN_GLOSS, COLUMN_TRANSLATION,
				COLUMN_ORTHOGRAPHY, COLUMN_CONTEXT, COLUMN_IMAGE_FILES,
				COLUMN_AUDIO_VIDEO_FILES, COLUMN_LOCATIONS, COLUMN_REMINDERS,
				COLUMN_TAGS, COLUMN_COMMENTS, COLUMN_VALIDATION_STATUS,
				COLUMN_ENTERED_BY_USER, COLUMN_MODIFIED_BY_USER };

		public static String[] currentColumns = version1Columns;

		// Offline Sample data
		private static ContentValues sampleData() {
			ContentValues values = new ContentValues();
			values.put(COLUMN_ID, "sample12345");
			values.put(COLUMN_MORPHEMES, "e'sig");
			values.put(COLUMN_GLOSS, "clam");
			values.put(COLUMN_TRANSLATION, "Clam");
			values.put(COLUMN_ORTHOGRAPHY, "e'sig");
			values.put(COLUMN_CONTEXT, " ");
			return values;
		}

		public static void setColumns() {
			DatumTable.columns = OPrimeTable.getBaseColumns();
			for (String column : currentColumns) {
				DatumTable.columns.add(column);
			}
		}
	}

}
