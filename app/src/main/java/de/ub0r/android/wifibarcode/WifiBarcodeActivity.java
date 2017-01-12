/*
 * Copyright (C) 2011-2012 Felix Bechstein
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

import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import de.ub0r.android.logg0r.Log;

/**
 * Main {@link SherlockActivity} showing wifi configuration and barcodes.
 *
 * @author flx
 */
public final class WifiBarcodeActivity extends SherlockActivity implements
        OnClickListener {

    /**
     * Tag for log output.
     */
    private static final String TAG = "WifiBarcodeActivity";

    /**
     * Extra: barcode's bitmap.
     */
    static final String EXTRA_BARCODE = "barcode";

    /**
     * Extra: barcode's title.
     */
    static final String EXTRA_TITLE = "title";

    /**
     * Cache barcodes.
     */
    private BarcodeCache barcodes;

    /**
     * Local {@link Spinner}s.
     */
    private Spinner mSpConfigs, mSpNetType;

    /**
     * Local {@link EditText}s.
     */
    private EditText mEtSsid, mEtPassword;

    /**
     * BarCode's background color.
     */
    private String bCBackgroundColor = "FFFFFF";

    /**
     * BarCode's size.
     */
    private String bCSize = "200x200";

    /**
     * Extra: Got root?
     */
    private static final String EXTRA_GOT_ROOT = "got_root";

    /**
     * False if runAsRoot failed.
     */
    private boolean gotRoot = true;

    private boolean mFirstLoad = true;

    /**
     * Cache barcodes.
     */
    private static class BarcodeCache extends HashMap<String, Bitmap> {

        /**
         * Serial Version UID.
         */
        private static final long serialVersionUID = -1563072588317040645L;

        /**
         * Cache dir.
         */
        private final File cacheDir;

        /**
         * Default constructor.
         *
         * @param context {@link Context}
         */
        public BarcodeCache(final Context context) {
            super();
            cacheDir = new File(getRealCacheDir(context), "barcodes");
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                Log.e(TAG, "error creating cache barcode cache dir");
            }
        }

        @Override
        public Bitmap get(final Object key) {
            Log.d(TAG, "cache.get(", key, ")");
            Bitmap b = super.get(key);
            if (b == null) {
                Log.v(TAG, "cache/miss");
                try {
                    // get bitmap from file system
                    String url = (String) key;
                    File f = getFile(url);
                    if (f.exists()) {
                        Log.i(TAG, "load barcode from file system..");
                        b = BitmapFactory.decodeStream(new FileInputStream(f));
                        if (b != null) {
                            // save barcode to memory cache
                            super.put(url, b);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "io error", e);
                }
            }
            return b;
        }

        /**
         * Load a bitmap from input stream and save it to file.
         *
         * @param key url
         * @param is  {@link InputStream}
         * @return {@link Bitmap}
         */
        public Bitmap put(final String key, final InputStream is) {
            Log.d(TAG, "cache.put(", key, ")");
            if (key == null || is == null) {
                return null;
            }
            // final Bitmap b = BitmapFactory.decodeStream(is);

            // super.put(key, b);
            // put bitmap to file system
            File f = getFile(key);
            Log.i(TAG, "file ", f.getAbsolutePath(), " does not exist, write it..");
            try {
                // f.createNewFile();
                FileOutputStream os = new FileOutputStream(f);

                byte[] buffer = new byte[1024];
                int l;
                while ((l = is.read(buffer)) > 0) {
                    Log.d(TAG, "write bytes: ", l);
                    os.write(buffer, 0, l);
                }
                os.close();
                Log.d(TAG, "done");
            } catch (IOException e) {
                Log.e(TAG, "error writing file: ", f.getAbsolutePath(), e);
            }

            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            super.put(key, b);
            return b;
        }

        /**
         * Get {@link File} object for a barcode's url.
         *
         * @param url url
         * @return {@link File}
         */
        private File getFile(final String url) {
            final File ret = new File(cacheDir, url2key(url));
            Log.d(TAG, "getFile(", url, "): ", ret.getAbsolutePath());
            return ret;
        }

        private String url2key(String url) {
            return url.replaceAll(".*chart\\?cht=", "").replaceAll("%", "_")
                    .replaceAll("\\|", "_").replaceAll("&", "_");
        }
    }

    /**
     * Show wifi configuration as {@link ArrayAdapter}.
     */
    private static class WifiAdapter extends ArrayAdapter<WifiConfiguration> {

        /**
         * Passwords.
         */
        private final HashMap<WifiConfiguration, String> passwords = new HashMap<>();

        /**
         * Default constructor.
         *
         * @param context            {@link Context}
         * @param textViewResourceId Resource for item views.
         */
        public WifiAdapter(final Context context, final int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            assert v != null;
            ((TextView) v.findViewById(android.R.id.text1)).setText(this
                    .getItem(position).SSID.replaceAll("\"", ""));
            return v;
        }

        @Override
        public View getDropDownView(final int position, final View convertView,
                                    final ViewGroup parent) {
            View v = super.getDropDownView(position, convertView, parent);
            assert v != null;
            ((TextView) v.findViewById(android.R.id.text1)).setText(this
                    .getItem(position).SSID.replaceAll("\"", ""));
            return v;
        }

        @Override
        public void clear() {
            super.clear();
            passwords.clear();
        }

        /**
         * Add a {@link WifiConfiguration} with password.
         *
         * @param object   {@link WifiConfiguration}
         * @param password password
         */
        public void add(final WifiConfiguration object, final String password) {
            add(object);
            passwords.put(object, password);
        }

        /**
         * Get password for {@link WifiConfiguration}.
         *
         * @param position position
         * @return password
         */
        public String getPassword(final int position) {
            WifiConfiguration wc = getItem(position);
            if (wc == null) {
                return null;
            }
            return passwords.get(wc);
        }
    }

    /**
     * Load barcodes in background.
     */
    private class BarcodeLoader extends AsyncTask<String, Void, Exception> {

        @Override
        protected Exception doInBackground(final String... url) {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            for (String u : url) {
                try {
                    Log.d(TAG, "load barcode: ", u);
                    HttpResponse repsonse = httpClient.execute(new HttpGet(u));
                    if (repsonse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        WifiBarcodeActivity.this.barcodes.put(u, repsonse
                                .getEntity().getContent());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "error fetching barcode", e);
                    return e;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Exception result) {
            WifiBarcodeActivity.this.showBarcode(true);
            if (result != null) {
                Toast.makeText(WifiBarcodeActivity.this, result.toString(),
                        Toast.LENGTH_LONG).show();
                Toast.makeText(WifiBarcodeActivity.this,
                        R.string.error_get_barcode, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Encloses the incoming string inside double quotes, if it isn't already quoted.
     *
     * @param string : the input string
     * @return a quoted string, of the form "input". If the input string is null, it returns null as
     * well.
     */
    private static String convertToQuotedString(final String string) {
        if (string == null) {
            return null;
        }
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int lastPos = string.length() - 1;
        if (lastPos < 0
                || (string.charAt(0) == '"' && string.charAt(lastPos) == '"')) {
            return string;
        }
        return '\"' + string + '\"';
    }

    /**
     * Run command as root.
     *
     * @param command command
     * @return true, if command was successfully executed
     */
    private static boolean runAsRoot(final String command) {
        Log.i(TAG, "running command as root: ", command);
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
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (savedInstanceState != null) {
            gotRoot = savedInstanceState.getBoolean(EXTRA_GOT_ROOT, true);
            mFirstLoad = savedInstanceState.getBoolean("mFirstLoad", true);
        } else {
            flushWifiPasswords();
        }

        barcodes = new BarcodeCache(this);

        WifiAdapter adapter = new WifiAdapter(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        findViewById(R.id.add).setOnClickListener(this);
        findViewById(R.id.barcode).setOnClickListener(this);
        mEtSsid = (EditText) findViewById(R.id.ssid);
        mEtPassword = (EditText) findViewById(R.id.password);
        mSpConfigs = (Spinner) findViewById(R.id.configurations);
        mSpNetType = (Spinner) findViewById(R.id.networktype);

        mSpConfigs.setAdapter(adapter);
        mSpConfigs.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent,
                                       final View view, final int position, final long id) {
                if (position == 0) {
                    WifiBarcodeActivity.this.mEtSsid.setText(null);
                    WifiBarcodeActivity.this.mEtSsid.setEnabled(true);
                    WifiBarcodeActivity.this.mSpNetType.setEnabled(true);
                    WifiBarcodeActivity.this.mSpNetType.setSelection(0);
                    WifiBarcodeActivity.this.mEtPassword.setText(null);
                    WifiBarcodeActivity.this.mEtPassword.setEnabled(true);
                } else {
                    WifiAdapter a = (WifiAdapter) WifiBarcodeActivity.this.mSpConfigs.getAdapter();
                    WifiConfiguration wc = a.getItem(position);
                    WifiBarcodeActivity.this.mEtSsid.setText(wc.SSID
                            .replaceAll("\"", ""));
                    WifiBarcodeActivity.this.mEtSsid.setEnabled(false);
                    int i = 0;
                    if (wc.allowedAuthAlgorithms
                            .get(WifiConfiguration.AuthAlgorithm.SHARED)) {
                        i = 1;
                    } else if (wc.allowedKeyManagement
                            .get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                        i = 2;
                    }
                    WifiBarcodeActivity.this.mSpNetType.setSelection(i);
                    WifiBarcodeActivity.this.mSpNetType.setEnabled(false);
                    String p = a.getPassword(position);
                    WifiBarcodeActivity.this.mEtPassword.setText(p);
                    WifiBarcodeActivity.this.mEtPassword.setEnabled(i != 0
                            && TextUtils.isEmpty(p));
                }
                WifiBarcodeActivity.this.showBarcode(true);
                WifiBarcodeActivity.this.findViewById(R.id.add).setVisibility(
                        View.GONE);

            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // nothing to do
            }

        });

        mSpNetType.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent,
                                       final View view, final int position, final long id) {
                //noinspection ConstantConditions
                String p = WifiBarcodeActivity.this.mEtPassword.getText()
                        .toString();
                WifiBarcodeActivity.this.mEtPassword.setEnabled(position != 0
                        && (WifiBarcodeActivity.this.mSpConfigs
                        .getSelectedItemPosition() == 0 || TextUtils
                        .isEmpty(p)));
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // nothing to do
            }
        });

        final int c = getResources().getColor(android.R.color.background_light);
        bCBackgroundColor = Integer.toHexString(Color.red(c))
                + Integer.toHexString(Color.green(c))
                + Integer.toHexString(Color.blue(c));
        bCSize = getString(R.string.barcode_size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_GOT_ROOT, gotRoot);
        outState.putBoolean("mFirstLoad", mFirstLoad);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getSupportMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected(", item.getItemId(), ")");
        switch (item.getItemId()) {
            case R.id.item_generate:
                showBarcode(false);
                return true;
            case R.id.item_wifi_config:
                startActivity(new Intent("android.settings.WIFI_SETTINGS"));
                return true;
            case R.id.item_scan:
                try {
                    Intent intent = new Intent(
                            "com.google.zxing.client.android.SCAN");
                    // intent.setPackage("com.google.zxing.client.android");
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                    startActivityForResult(intent, 0);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "failed launching scanner", e);
                    Builder b = new Builder(this);
                    b.setTitle(R.string.install_barcode_scanner_);
                    b.setMessage(R.string.install_barcode_scanner_hint);
                    b.setNegativeButton(android.R.string.cancel, null);
                    b.setPositiveButton(R.string.install,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog,
                                                    final int which) {
                                    // FIXME: install "com.google.zxing.client.android"
                                }
                            }
                    );
                    b.show();
                }
                return true;
            case R.id.item_about:
                startActivity(new Intent(this, About.class));
                return true;
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 final Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                final String contents = intent.getStringExtra("SCAN_RESULT");
                Log.d(TAG, "got qr code: ", contents);
                parseResult(contents);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.add:
                addWifi();
                break;
            case R.id.barcode:
                final String url = getUrl();
                final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
                        this, ViewerActivity.class);
                i.putExtra(EXTRA_BARCODE, barcodes.get(url));
                //noinspection ConstantConditions
                i.putExtra(EXTRA_TITLE, mEtSsid.getText().toString());
                startActivity(i);
                break;
            default:
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadWifiConfigurations();
    }

    /**
     * Load wifi configurations.
     */
    private void loadWifiConfigurations() {
        WifiAdapter adapter = (WifiAdapter) mSpConfigs.getAdapter();
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        List<WifiConfiguration> wcs = wm.getConfiguredNetworks();
        String currentSSID = wm.getConnectionInfo().getSSID();
        Log.d(TAG, "currentSSID=", currentSSID);
        WifiConfiguration custom = new WifiConfiguration();
        custom.SSID = getString(R.string.custom);
        adapter.clear();
        adapter.add(custom);
        flushWifiPasswords();
        Log.d(TAG, "#wcs=", wcs == null ? "null" : wcs.size());
        if (wcs != null) {
            int selected = -1;
            for (WifiConfiguration wc : wcs) {
                adapter.add(wc, getWifiPassword(wc));
                Log.d(TAG, "wc.SSID=", wc.SSID);
                if (mFirstLoad && currentSSID != null && currentSSID.equals(wc.SSID)) {
                    selected = adapter.getCount() - 1;
                    Log.d(TAG, "selected=", selected);
                }
            }
            if (selected > 0) {
                // mFirstLoad == true
                mSpConfigs.setSelection(selected);
            }
            mFirstLoad = false;
        }
    }

    /**
     * Add wifi configuration.
     */
    private void addWifi() {
        WifiConfiguration wc = new WifiConfiguration();
        wc.allowedAuthAlgorithms.clear();
        wc.allowedGroupCiphers.clear();
        wc.allowedKeyManagement.clear();
        wc.allowedPairwiseCiphers.clear();
        wc.allowedProtocols.clear();

        //noinspection ConstantConditions
        wc.SSID = convertToQuotedString(mEtSsid.getText().toString());
        wc.hiddenSSID = true;

        //noinspection ConstantConditions
        String password = mEtPassword.getText().toString();

        switch (mSpNetType.getSelectedItemPosition()) {
            case 1: // WEP
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wc.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.SHARED);
                int length = password.length();
                // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                if ((length == 10 || length == 26 || length == 58)
                        && password.matches("[0-9A-Fa-f]*")) {
                    wc.wepKeys[0] = password;
                } else {
                    wc.wepKeys[0] = '"' + password + '"';
                }
                break;
            case 2: // WPA
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                if (password.matches("[0-9A-Fa-f]{64}")) {
                    wc.preSharedKey = password;
                } else {
                    wc.preSharedKey = '"' + password + '"';
                }
                break;
            default: // OPEN
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
        }

        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        int netId = wm.addNetwork(wc);
        int msg;
        final boolean ret = wm.saveConfiguration();
        if (ret) {
            wm.enableNetwork(netId, false);
            msg = R.string.wifi_added;
        } else {
            msg = R.string.wifi_failed;
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Parse result from QR Code.
     *
     * @param result content from qr code
     */
    private void parseResult(final String result) {
        Log.d(TAG, "parseResult(", result, ")");
        if (result == null || !result.startsWith("WIFI:")) {
            Log.e(TAG, "error parsing result: ", result);
            Toast.makeText(this, R.string.error_read_barcode, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        String[] c = result.substring("WIFI:".length()).split(";", 3);
        for (String line : c) {
            if (line.startsWith("S:")) {
                mEtSsid.setText(line.substring(2));
            } else if (line.startsWith("T:NOPASS")) {
                mSpNetType.setSelection(0);
            } else if (line.startsWith("T:WEP")) {
                mSpNetType.setSelection(1);
            } else if (line.startsWith("T:WPA")) {
                mSpNetType.setSelection(2);
            } else if (line.startsWith("P:")) {
                mEtPassword.setText(line.substring(2).replaceAll(";?;$",
                        ""));
            }
        }

        mSpConfigs.setSelection(0);

        findViewById(R.id.add).setVisibility(View.VISIBLE);
    }

    /**
     * Flush wifi password cache.
     */
    private void flushWifiPasswords() {
        final File f = getCacheFile();
        if (f.exists() && !f.delete()) {
            Log.e(TAG, "error deleting file: ", getCacheFile().getAbsolutePath());
        }
    }

    private static File getRealCacheDir(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return context.getExternalCacheDir();
        } else {
            return context.getCacheDir();
        }
    }

    /**
     * @return file holding cached wpa_supplicant.conf.
     */
    private File getCacheFile() {
        return new File(getRealCacheDir(this), "wpa_supplicant.conf");
    }

    /**
     * Get WiFi password.
     *
     * @param wc {@link WifiConfiguration}
     * @return password
     */
    private String getWifiPassword(final WifiConfiguration wc) {
        Log.d(TAG, "getWifiPassword(", wc, ")");
        File f = getCacheFile();
        if (!f.exists()) {
            if (gotRoot) {
                final String tpl = "pkgid=$(grep '%s' /data/system/packages.list | cut -d' ' -f2)\n"
                        + "cat /data/misc/wifi/wpa_supplicant.conf > '%s'\n"
                        + "chown $pkgid:$pkgid '%s'\n"
                        + "chmod 644 '%s'";
                final String command = String.format(tpl, BuildConfig.APPLICATION_ID, f.getAbsolutePath(), f.getAbsolutePath(), f.getAbsolutePath());
                if (!runAsRoot(command)) {
                    Toast.makeText(this, R.string.error_need_root,
                            Toast.LENGTH_LONG).show();
                    gotRoot = false;
                    return null;
                }
            } else {
                Log.w(TAG, "gotRoot=false");
                return null;
            }
        }
        f = new File(f.getAbsolutePath());
        if (!f.exists()) {
            Toast.makeText(this, R.string.error_read_file, Toast.LENGTH_LONG)
                    .show();
            return null;
        }
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
                        } else {
                            len = "wep_key0=".length();
                        }
                        if (i < 0) {
                            return null;
                        }

                        return config.substring(i + len + 1,
                                config.indexOf("\n", i) - 1);

                    }
                    sb = new StringBuffer();
                }
                sb.append(l).append("\n");
            }
            br.close();
        } catch (IOException e) {
            Log.e(TAG, "error reading file", e);
            Toast.makeText(this, R.string.error_read_file, Toast.LENGTH_LONG)
                    .show();
            return null;
        }
        return null;
    }

    /**
     * Get current BarCode's URL.
     *
     * @return URL
     */
    private String getUrl() {
        String url = "http://chart.apis.google.com/" + "chart?cht=qr&chs="
                + bCSize + "&chld=2&chf=bg,s," + bCBackgroundColor
                + "&chl=";
        int type = mSpNetType.getSelectedItemPosition();
        String[] types = getResources().getStringArray(
                R.array.networktypes);
        StringBuilder sb = new StringBuilder();
        sb.append("WIFI:T:");
        sb.append(types[type]);
        sb.append(";S:");
        sb.append(mEtSsid.getText());
        sb.append(";P:");
        if (type == 0) {
            sb.append("nopass");
        } else {
            sb.append(mEtPassword.getText());
        }
        sb.append(";;");
        try {
            url += URLEncoder.encode(sb.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "error url encoding barcode");
        }
        return url;
    }

    /**
     * Show barcode.
     *
     * @param cacheOnly load only from cache
     */
    private void showBarcode(final boolean cacheOnly) {
        final String url = getUrl();
        if (!cacheOnly && !this.barcodes.containsKey(url)) {
            Log.i(TAG, "barcode not available, load it...");
            BarcodeLoader loader = new BarcodeLoader();
            loader.execute(url);
        }
        ImageView iv = (ImageView) findViewById(R.id.barcode);
        Bitmap bc = barcodes.get(url);
        if (bc != null) {
            iv.setImageBitmap(bc);
            iv.setVisibility(View.VISIBLE);
            findViewById(R.id.c2e).setVisibility(View.VISIBLE);
            findViewById(R.id.progress).setVisibility(View.GONE);
        } else {
            iv.setVisibility(View.GONE);
            findViewById(R.id.c2e).setVisibility(View.GONE);
            if (!cacheOnly) {
                findViewById(R.id.progress).setVisibility(View.VISIBLE);
            }
        }
        if (cacheOnly) {
            findViewById(R.id.progress).setVisibility(View.GONE);
        }
    }
}