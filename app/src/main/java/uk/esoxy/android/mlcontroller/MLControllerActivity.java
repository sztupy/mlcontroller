package uk.esoxy.android.mlcontroller;

import uk.esoxy.android.mlcontroller.driver.NativeDevice;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

public class MLControllerActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private LoadInformationTask infotask;
	private ProgressDialog dialog = null;
	private boolean noinit;

	public static final String SELECT_USB_DEVICE_API = "usb_device_selected_api";
	public static final String SELECT_USB_DEVICE_KERNEL = "usb_device_selected_kernel";
	public static final String KERNEL_DEVICE = "usb_device_kernel";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		noinit = true;
		infotask = null;

		Preference myPref = (Preference) findPreference("refresh");
		myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				Refresh();
				return true;
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (infotask != null) {
			infotask.cancel(false);
			infotask = null;
		}
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		noinit = true;
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onResume() {
		super.onResume();
		noinit = true;
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		Refresh();
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updatePrefSummary(findPreference(key), false);
	}

	public void Refresh() {
		noinit = true;
		if (infotask != null) {
			infotask.cancel(false);
		}
		infotask = new LoadInformationTask();
		infotask.execute();
	}

	private void initSummary(Preference p) {
		if (p instanceof PreferenceCategory) {
			PreferenceCategory pCat = (PreferenceCategory) p;
			for (int i = 0; i < pCat.getPreferenceCount(); i++) {
				initSummary(pCat.getPreference(i));
			}
		} else {
			updatePrefSummary(p, true);
		}

	}

	private void updatePrefSummary(Preference p, boolean isInit) {
		if (noinit) isInit = true;
		if (p == null) {
			return;
		} else if (p.getKey() == null) {
			return;
		} else if (p.getKey().equals("hw_accel")) {
			CheckBoxPreference hwaccel = (CheckBoxPreference)p;
			if (NativeDevice.getNeon()) {
				hwaccel.setEnabled(true);
			} else {
				hwaccel.setEnabled(false);
				hwaccel.setChecked(false);
				hwaccel.setSummaryOff(R.string.hw_accel_unavail);
			}
		} else if (p instanceof ListPreference) {
			ListPreference listPref = (ListPreference) p;
			p.setSummary(listPref.getEntry());
			if (p.getKey().equals("driver_mode")) {
				if (!isInit) {
					Refresh();
				}
			}
		} else if (p instanceof EditTextPreference) {
			EditTextPreference editTextPref = (EditTextPreference) p;
			p.setSummary(editTextPref.getText());
		}
	}

	private class LoadInformationTask extends AsyncTask<Void, Object, String> {
		private int prefType;

		@Override
		protected void onPreExecute() {
			if (dialog != null) {
				dialog.dismiss();
			}
			dialog = ProgressDialog.show(MLControllerActivity.this, "", getText(R.string.please_wait));
			PreferenceCategory myPref = (PreferenceCategory) findPreference("device_list");
			myPref.removeAll();
			if (((ListPreference) findPreference("driver_mode")).getValue().equals("api")) {
				prefType = 0;
			} else {
				prefType = 1;
			}
		}

		@Override
		protected String doInBackground(Void... args) {
			if (prefType == 0 && Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB) {
				UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
				HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
				Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
				int count = 0;
				while (deviceIterator.hasNext()) {
					if (isCancelled()) return null;
					UsbDevice device = deviceIterator.next();
					publishProgress(device);
					count++;
				}
				if (count == 0) publishProgress(getText(R.string.no_device_found));
			} else if (prefType == 1) {
				int count = 0;
				try {
					if (!NativeDevice.isNativeOkay()) throw new Exception("Couldn't load native interface!");
					File hubs = new File("/sys/bus/usb/devices").getCanonicalFile();
					if (hubs.isDirectory()) {
						for (File element : hubs.listFiles()) {
							if (isCancelled()) return null;
							if (!element.getName().startsWith("usb")) {
								try {
									NativeDevice d = new NativeDevice(element,(PreferenceManager.getDefaultSharedPreferences(MLControllerActivity.this).getBoolean("hw_accel", false)));
									publishProgress(d);
									count++;
								} catch (Exception e) {
								}
							}
						}
					}
					if (count == 0) publishProgress(getText(R.string.no_device_found));
				} catch (Exception e) {
					publishProgress(getText(R.string.driver_unavailable));
				}
			} else {
				publishProgress(getText(R.string.driver_unavailable));
			}
			return null;
		}

		@Override
		protected void onCancelled(String result) {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
				initSummary(getPreferenceScreen().getPreference(i));
			}
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}
			noinit = false;
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			Preference p = new Preference(MLControllerActivity.this);
			PreferenceCategory myPref = (PreferenceCategory) findPreference("device_list");
			if (values[0] instanceof NativeDevice) {
				final NativeDevice d = (NativeDevice) values[0];
				String s = String.format("%04x:%04x %s %s (%s)", d.getIdVendor(), d.getIdProduct(), d.getManufacturer(), d.getProduct(), d.getSerial());
				p.setTitle(s);
				if (d.getIdVendor() == 1193) {
					p.setEnabled(true);
					p.setSummary(R.string.device_start);
					if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB) {
						p.setIcon(R.drawable.ic_launcher);
					}
					p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
						public boolean onPreferenceClick(Preference arg0) {
							if (d.open(true)) {
								Intent i = new Intent(MLControllerActivity.this, MLControllerDisplayActivity.class);
								i.setAction(MLControllerActivity.SELECT_USB_DEVICE_KERNEL);
								i.putExtra(MLControllerActivity.KERNEL_DEVICE, d);
								startActivity(i);
							}
							return true;
						}
					});
				} else p.setEnabled(false);
			} else if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB && values[0] instanceof UsbDevice) {
				final UsbDevice d = (UsbDevice) values[0];

				String s = String.format("%04x:%04x %s", d.getVendorId(), d.getProductId(), d.getDeviceName());
				p.setTitle(s);
				if (d.getVendorId() == 1193) {
					p.setEnabled(true);
					p.setSummary(R.string.device_start);
					p.setIcon(R.drawable.ic_launcher);
					p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
						public boolean onPreferenceClick(Preference arg0) {
							Intent i = new Intent(MLControllerActivity.this, MLControllerDisplayActivity.class);
							i.setAction(MLControllerActivity.SELECT_USB_DEVICE_API);
							i.putExtra(UsbManager.EXTRA_DEVICE, d);
							startActivity(i);
							return true;
						}
					});
				} else p.setEnabled(false);
			} else {
				String s = values[0].toString();
				p.setTitle(s);
				p.setEnabled(false);
			}
			myPref.addPreference(p);
		}
	}
}