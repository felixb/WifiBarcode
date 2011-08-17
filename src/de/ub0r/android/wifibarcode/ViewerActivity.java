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

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.MenuItem;
import android.widget.ImageView;

/**
 * Show a barcode in full screen.
 * 
 * @author flx
 */
public final class ViewerActivity extends FragmentActivity {
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.viewer);
		this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onResume() {
		super.onResume();
		final Intent i = this.getIntent();
		Bitmap bitmap = i.getParcelableExtra(WifiBarcodeActivity.EXTRA_BARCODE);
		if (bitmap == null) {
			this.finish();
		} else {
			ImageView iv = (ImageView) this.findViewById(R.id.barcode);
			iv.setImageBitmap(bitmap);
			String s = i.getStringExtra(WifiBarcodeActivity.EXTRA_TITLE);
			this.getSupportActionBar().setSubtitle(s);
		}
	}

	/**
	 *{@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			this.finish();
			return true;
		default:
			return false;
		}
	}
}
