/*
 * Copyright (C) 2011 Felix Bechstein
 * 
 * This file is part of WifiBarcode.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.wifibarcode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import de.ub0r.android.lib.Log;

public class WifiBarcodeActivity extends FragmentActivity {
	/** Tag for log output. */
	private static final String TAG = "wba";

	/** Cache barcodes. */
	private HashMap<String, Bitmap> barcodes = new HashMap<String, Bitmap>();

	private static class WifiAdapter extends ArrayAdapter<WifiConfiguration> {
		public WifiAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);
			((TextView) v.findViewById(android.R.id.text1))
					.setText(getItem(position).SSID.replaceAll("\"", ""));
			return v;
		}

		@Override
		public View getDropDownView(int position, View convertView,
				ViewGroup parent) {
			View v = super.getDropDownView(position, convertView, parent);
			((TextView) v.findViewById(android.R.id.text1))
					.setText(getItem(position).SSID.replaceAll("\"", ""));
			return v;
		}
	}

	private class BarcodeLoader extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... url) {
			DefaultHttpClient httpClient = new DefaultHttpClient();
			for (String u : url) {
				try {
					Log.d(TAG, "load barcode: " + u);
					HttpResponse repsonse = httpClient.execute(new HttpGet(u));
					if (repsonse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
						Bitmap result = BitmapFactory.decodeStream(repsonse
								.getEntity().getContent());
						barcodes.put(u, result);
					}
				} catch (IOException e) {
					Log.e(TAG, "error fetching barcode", e);
					// TODO: show error message
				}
			}
			return null;
		}

		protected void onPostExecute(Void result) {
			showBarcode(true);
		}
	}

	/**
	 * Run command as root.
	 * 
	 * @param command
	 *            command
	 * @return true, if command was successfully executed
	 */
	public static boolean runAsRoot(String command) {
		Log.i(TAG, "running command as root: " + command);
		try {
			Runtime r = Runtime.getRuntime();
			Process p = r.exec("su");
			DataOutputStream d = new DataOutputStream(p.getOutputStream());
			d.writeBytes(command);
			d.writeBytes("\nexit\n");
			d.flush();
			int retval = p.waitFor();
			Log.i(TAG, "done");
			return (retval == 0);
		} catch (Exception e) {
			Log.e(TAG, "runAsRoot", e);
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
		List<WifiConfiguration> wcs = wm.getConfiguredNetworks();
		WifiAdapter adapter = new WifiAdapter(this,
				android.R.layout.simple_spinner_item);
		adapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		WifiConfiguration custom = new WifiConfiguration();
		custom.SSID = getString(R.string.custom);
		adapter.add(custom);
		for (WifiConfiguration wc : wcs) {
			adapter.add(wc);
		}
		final Spinner sp = (Spinner) findViewById(R.id.configurations);
		sp.setAdapter(adapter);
		sp.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				EditText etSsid = (EditText) findViewById(R.id.ssid);
				Spinner spNetType = (Spinner) findViewById(R.id.networktype);
				EditText etPwd = (EditText) findViewById(R.id.password);
				if (position == 0) {
					etSsid.setText(null);
					etSsid.setEnabled(true);
					spNetType.setEnabled(true);
					spNetType.setSelection(0);
					etPwd.setText(null);
					etPwd.setEnabled(true);
				} else {
					WifiConfiguration wc = ((WifiAdapter) sp.getAdapter())
							.getItem(position);
					etSsid.setText(wc.SSID.replaceAll("\"", ""));
					etSsid.setEnabled(false);
					int i = 0;
					if (wc.allowedAuthAlgorithms
							.get(WifiConfiguration.AuthAlgorithm.SHARED)) {
						i = 1;
					} else if (wc.allowedKeyManagement
							.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
						i = 2;
					}
					spNetType.setSelection(i);
					spNetType.setEnabled(false);
					etPwd.setText(getWifiPAssword(wc));
					etPwd.setEnabled(false);
				}
				showBarcode(true);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// nothing to do
			}

		});

		((Spinner) findViewById(R.id.networktype))
				.setOnItemSelectedListener(new OnItemSelectedListener() {
					@Override
					public void onItemSelected(AdapterView<?> parent,
							View view, int position, long id) {
						findViewById(R.id.password).setEnabled(position != 0);
					}

					@Override
					public void onNothingSelected(AdapterView<?> parent) {
						// nothing to do
					}
				});
	}

	/**
	 * {@inheritDoc}
	 */
	public final boolean onCreateOptionsMenu(final Menu menu) {
		this.getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	/**
	 *{@inheritDoc}
	 */
	public final boolean onOptionsItemSelected(final MenuItem item) {
		Log.d(TAG, "onOptionsItemSelected(" + item.getItemId() + ")");
		switch (item.getItemId()) {
		case R.id.item_generate:
			showBarcode(false);
			return true;
		case R.id.item_wifi_config:
			startActivity(new Intent("android.settings.WIFI_SETTINGS"));
			return true;
		default:
			return false;
		}
	}

	/**
	 * Get WiFi password.
	 * 
	 * @param wc
	 *            {@link WifiConfiguration}
	 * @return password
	 */
	private String getWifiPAssword(WifiConfiguration wc) {
		final String targetFile = getCacheDir().getAbsolutePath()
				+ "/wpa_supplicant.conf";
		final String command = "cat /data/misc/wifi/wpa_supplicant.conf > "
				+ targetFile;
		if (!runAsRoot(command)) {
			Toast.makeText(this, R.string.error_need_root, Toast.LENGTH_LONG)
					.show();
			return null;
		}
		File f = new File(targetFile);
		try {
			BufferedReader br = new BufferedReader(new FileReader(f));
			String l;
			StringBuffer sb = new StringBuffer();
			while ((l = br.readLine()) != null) {
				if (l.startsWith("network=") || l.equals("}")) {
					String config = sb.toString();
					// parse it
					if (config.contains("ssid=" + wc.SSID)) {
						Log.d(TAG, "wifi config:");
						Log.d(TAG, config);
						int i = config.indexOf("wep_key0=");
						int len;
						if (i < 0) {
							i = config.indexOf("psk=");
							len = "psk=".length();
						} else
							len = "wep_key0=".length();
						if (i < 0) return null;

						return config.substring(i + len + 1, config.indexOf(
								"\n", i) - 1);

					}
					sb = new StringBuffer();
				}
				sb.append(l + "\n");
			}
			br.close();
			f.delete();
		} catch (IOException e) {
			Toast.makeText(this, R.string.error_read_file, Toast.LENGTH_LONG)
					.show();
			return null;
		}
		return null;
	}

	/**
	 * Show barcode.
	 * 
	 * @param cacheOnly
	 *            load only from cache
	 */
	private void showBarcode(final boolean cacheOnly) {
		String url = "http://chart.apis.google.com/chart?cht=qr&chs=350x350&chl=";
		StringBuffer sb = new StringBuffer();
		sb.append("WIFI:S:");
		sb.append(((EditText) findViewById(R.id.ssid)).getText());
		sb.append(";T:");
		sb.append(((Spinner) findViewById(R.id.networktype)).getSelectedItem());
		sb.append(";P:");
		sb.append(((EditText) findViewById(R.id.password)).getText());
		sb.append(";;");
		url += URLEncoder.encode(sb.toString());
		if (!cacheOnly && !barcodes.containsKey(url)) {
			Log.i(TAG, "barcode not available, load it...");
			BarcodeLoader loader = new BarcodeLoader();
			loader.execute(url);
		}
		ImageView iv = (ImageView) findViewById(R.id.barcode);
		Bitmap bc = barcodes.get(url);
		if (bc != null) {
			iv.setImageBitmap(bc);
			iv.setVisibility(View.VISIBLE);
			findViewById(R.id.progress).setVisibility(View.GONE);
		} else {
			iv.setVisibility(View.GONE);
			if (!cacheOnly) {
				findViewById(R.id.progress).setVisibility(View.VISIBLE);
			}
		}
		if (cacheOnly) {
			findViewById(R.id.progress).setVisibility(View.GONE);
		}
	}
}