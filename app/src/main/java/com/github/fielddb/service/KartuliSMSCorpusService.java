package com.github.fielddb.service;

import java.util.ArrayList;

import com.github.fielddb.database.DatumContentProvider;
import com.github.fielddb.database.DatumContentProvider.DatumTable;
import com.github.fielddb.Config;
import com.github.fielddb.model.Datum;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class KartuliSMSCorpusService extends IntentService {
  private ArrayList<Datum> smsSamples;

  public KartuliSMSCorpusService(String name) {
    super(name);
    initSmsSamples();
  }

  public KartuliSMSCorpusService() {
    super("DownloadDatumsService");
    initSmsSamples();
  }

  @Override
  protected void onHandleIntent(Intent arg0) {

    String id = "";
    Uri uri;
    String[] datumProjection = { DatumTable.COLUMN_ID };
    Cursor cursor;
    ContentValues datumAsValues;
    for (Datum datum : smsSamples) {
      id = datum.getId();
      uri = Uri.withAppendedPath(DatumContentProvider.CONTENT_URI, id);
      cursor = getContentResolver().query(uri, datumProjection, null, null, null);

      // TODO instead, update it, without losing info
      if (cursor == null || cursor.getCount() <= 0) {
        /* save it */
        try {
          datumAsValues = new ContentValues();
          datumAsValues.put(DatumTable.COLUMN_ID, id);
          datumAsValues.put(DatumTable.COLUMN_REV, datum.getRev());
          datumAsValues.put(DatumTable.COLUMN_UTTERANCE, datum.getUtterance());
          datumAsValues.put(DatumTable.COLUMN_ORTHOGRAPHY, datum.getOrthography());
          datumAsValues.put(DatumTable.COLUMN_CONTEXT, datum.getContext());
          datumAsValues.put(DatumTable.COLUMN_TAGS, datum.getTagsString());
          if (!"instructions".equals(id)) {
            Intent transferFile = new Intent(getApplicationContext(), DownloadFilesService.class);
            transferFile.putExtra(Config.EXTRA_RESULT_FILENAME, "sms_selected.png");
            getApplicationContext().startService(transferFile);
            datumAsValues.put(DatumTable.COLUMN_IMAGE_FILES, "sms_selected.png");
          }
          uri = getContentResolver().insert(DatumContentProvider.CONTENT_URI, datumAsValues);
        } catch (Exception e) {
          Log.d(Config.TAG, "Failed to insert this sample most likely something was missing from the server...");
          e.printStackTrace();
        }
      }
      if (cursor != null) {
        cursor.close();
      }
    }

    Intent updateWebSearchSamples = new Intent(getApplicationContext(), KartuliWebSearchCorpusService.class);
    getApplicationContext().startService(updateWebSearchSamples);
  }

  private void initSmsSamples() {
    this.smsSamples = new ArrayList<Datum>();

    Datum datum = new Datum(
        "შენ უნდა წაიკითხო რამოდენიმე წინადადება, რათა გადაამზადო აპლიკაცია შენს ხმაზე და შენს სიტყვებზე");
    datum.setUtterance("You need to read a few sentences to train the recognizer to your voice and your words.");
    datum.setId("instructions");
    datum.setRev("");
    datum
        .setContext("The Georgian  language is very complex and very different from other languages which were used to build Speech Recognition systems. This means each person should have their own recognizer.");
    datum.setTagsFromSting("Instructions");
    this.smsSamples.add(datum);

    datum = new Datum("სად ხარ??");
    datum.setUtterance("sad xar??");
    datum.setId("sms1");
    datum.setRev("");
    datum.setContext("");
    datum.setTagsFromSting("SMS");
    this.smsSamples.add(datum);

    datum = new Datum("ახლა არ მცალია და საგამოს გადმოვალ.");
    datum.setUtterance("axla ar mcalia da sagamos gadmoval.");
    datum.setId("sms2");
    datum.setRev("");
    datum.setContext("");
    datum.setTagsFromSting("SMS");
    this.smsSamples.add(datum);

  }
}
