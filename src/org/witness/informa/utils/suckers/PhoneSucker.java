package org.witness.informa.utils.suckers;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.SensorLogger;
import org.witness.sscphase1.ObscuraApp;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

@SuppressWarnings("rawtypes")
public class PhoneSucker extends SensorLogger {
	TelephonyManager tm;
	BluetoothAdapter ba;
	
	boolean hasBluetooth = false;
	
	@SuppressWarnings("unchecked")
	public PhoneSucker(Context c) {
		super(c);
		setSucker(this);
				
		tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		ba = BluetoothAdapter.getDefaultAdapter();
		
		if(ba != null)
		{
			hasBluetooth = true;
			// if bluetooth is off, turn it on... (be sure to turn off when finished)
			if(!ba.isEnabled())
				ba.enable();
	
			
		
		}
		else
			Log.d(InformaConstants.TAG,"no bt?");
		
		// TODO: if bluetooth is off, turn it on... (be sure to turn off when finished)
		setTask(new TimerTask() {
			
			@Override
			public void run() throws NullPointerException {
				if(getIsRunning()) {
					try {
						sendToBuffer(jPack(InformaConstants.Keys.Suckers.Phone.CELL_ID, getCellId()));
						
						// find other bluetooth devices around
						if(hasBluetooth && !ba.isDiscovering())
							ba.startDiscovery();
						
					} catch (JSONException e) {}
				}
			}
		});
		
		getTimer().schedule(getTask(), 0, InformaConstants.Suckers.LogRate.PHONE);
	}
	
	public String getIMEI() {
		try {
			Log.d(InformaConstants.SUCKER_TAG, tm.getDeviceId());
			return tm.getDeviceId();
		} catch(NullPointerException e) {
			Log.e(InformaConstants.TAG,"getIMEI error",e);
			return null;
		}
	}
	
	public String getCellId() {	
		try {
			String out = "";
			if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
				final GsmCellLocation gLoc = (GsmCellLocation) tm.getCellLocation();
				out = Integer.toString(gLoc.getCid());
			} else if(tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
				final CdmaCellLocation cLoc = (CdmaCellLocation) tm.getCellLocation();
				out = Integer.toString(cLoc.getBaseStationId());
			}
			return out;
		} catch(NullPointerException e) {
			return null;
		}
	}
	
	public List<String> getWifiNetworks() {
		List<String> wifi = new ArrayList<String>();
		
		return wifi;
	}
	
	public JSONObject forceReturn() {
		try {
			JSONObject fr = new JSONObject();
			fr.put(InformaConstants.Keys.Suckers.Phone.IMEI, getIMEI());
			fr.put(InformaConstants.Keys.Suckers.Phone.BLUETOOTH_DEVICE_ADDRESS, ba.getAddress());
			fr.put(InformaConstants.Keys.Suckers.Phone.BLUETOOTH_DEVICE_NAME, ba.getName());
			fr.put(InformaConstants.Keys.Suckers.Phone.CELL_ID, getCellId());
			Log.d(InformaConstants.SUCKER_TAG, fr.toString());
			return fr;
		} catch (JSONException e) {
			return null;
		}
		catch(NullPointerException e) {
			return null;
		}
		
	}
	
	public void stopUpdates() {
		setIsRunning(false);
		if(hasBluetooth && ba.isDiscovering()) {
			ba.cancelDiscovery();
			ba.disable();
		}
		
		Log.d(InformaConstants.TAG, "shutting down PhoneSucker...");
	}

}