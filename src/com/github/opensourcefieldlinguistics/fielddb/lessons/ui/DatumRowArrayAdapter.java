package com.github.opensourcefieldlinguistics.fielddb.lessons.ui;

import java.util.List;

import com.github.opensourcefieldlinguistics.fielddb.lessons.R;
import com.github.opensourcefieldlinguistics.fielddb.model.Datum;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * 
 * http://www.vogella.com/tutorials/AndroidListView/article.html#
 * adapterown_custom
 */
public class DatumRowArrayAdapter extends ArrayAdapter<Datum> {
	private final Context context;
	private final List<Datum> values;

	public DatumRowArrayAdapter(Context context, List<Datum> items) {
		super(context, R.layout.datum_list_row, items);
		this.context = context;
		this.values = items;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.datum_list_row, parent, false);
		TextView textView = (TextView) rowView.findViewById(R.id.orthography);
		// ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
		textView.setText(values.get(position).getOrthography());
		textView = (TextView) rowView.findViewById(R.id.translation);
		String translation = values.get(position).getTranslation();
		if (translation != null && !"".equals(translation)) {
			translation = " - " + translation;
		}
		textView.setText(translation);
		// change the icon for Windows and iPhone
		// String s = values[position];
		// if (s.startsWith("iPhone")) {
		// imageView.setImageResource(R.drawable.no);
		// } else {
		// imageView.setImageResource(R.drawable.ok);
		// }

		return rowView;
	}
}