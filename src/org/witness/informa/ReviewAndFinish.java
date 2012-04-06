package org.witness.informa;

import java.io.File;

import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.informa.utils.InformaConstants.LoginCache;
import org.witness.informa.utils.secure.Apg;
import org.witness.ssc.InformaApp;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ReviewAndFinish extends Activity implements OnClickListener {
	Button confirmView, confirmQuit, confirmTakeAnother;
	Uri savedImageUri;
	Handler finish;
	Apg apg;
	SharedPreferences _sp;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reviewandfinish);
		savedImageUri = getIntent().getData();
		Log.d(InformaConstants.TAG, "HERE IS THE URI: " + savedImageUri.getPath());
		
		_sp = PreferenceManager.getDefaultSharedPreferences(this);
		
		confirmView = (Button) findViewById(R.id.informaConfirm_btn_view);
		confirmView.setOnClickListener(this);
		
		confirmQuit = (Button) findViewById(R.id.informaConfirm_btn_quit);
		confirmQuit.setOnClickListener(this);
		
		confirmTakeAnother = (Button) findViewById(R.id.informaConfirm_btn_takeAnother);
		confirmTakeAnother.setOnClickListener(this);
		
    	if(Integer.parseInt(_sp.getString(Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, "")) == LoginCache.AFTER_SAVE)
    		_sp.edit().putString(Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
	}
	
    private void viewImage() {
    	
    	Intent iView = new Intent(Intent.ACTION_VIEW);
    	iView.setType(ObscuraConstants.MIME_TYPE_JPEG);
    	iView.putExtra(Intent.EXTRA_STREAM, savedImageUri);
    	iView.setDataAndType(savedImageUri, ObscuraConstants.MIME_TYPE_JPEG);

    	startActivity(Intent.createChooser(iView, "View Image"));
    	finish();
	
    }
    
    private void viewVideo() {
    	Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
    	intent.setDataAndType(savedImageUri, ObscuraConstants.MIME_TYPE_MP4);    	
   	 	startActivity(intent);
   	 	finish();
    }
    
    public File pullPathFromUri(Uri uri) {

    	String originalImageFilePath = null;

    	if (uri.getScheme() != null && uri.getScheme().equals("file"))
    	{
    		originalImageFilePath = uri.toString();
    	}
    	else
    	{
	    	String[] columnsToSelect = { MediaStore.Images.Media.DATA };
	    	Cursor imageCursor = getContentResolver().query(uri, columnsToSelect, null, null, null );
	    	if ( imageCursor != null && imageCursor.getCount() == 1 ) {
		        imageCursor.moveToFirst();
		        originalImageFilePath = imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA));
	    	}
    	}

    	return new File(originalImageFilePath);
    }
    

	@Override
	public void onClick(View v) {
		if(v == confirmView) {	
			switch(getIntent().getIntExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO)) {
			case InformaConstants.MediaTypes.PHOTO:
				viewImage();
				break;
			case InformaConstants.MediaTypes.VIDEO:
				viewVideo();
				break;
			}
		} else if(v == confirmQuit) {			
			if(Integer.parseInt(_sp.getString(Keys.Settings.DB_PASSWORD_CACHE_TIMEOUT, "")) == LoginCache.ON_CLOSE)
	    		_sp.edit().putString(Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).commit();
			startActivity(new Intent(this, InformaApp.class).putExtra(Keys.Service.FINISH_ACTIVITY, "die"));
		} else if(v == confirmTakeAnother) {
			startActivity(new Intent(this, InformaApp.class).putExtra(Keys.Service.START_SERVICE, "go"));
		}
		finish();
	}
}
