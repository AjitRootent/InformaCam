package org.witness.ssc.image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.witness.informa.KeyChooser;
import org.witness.informa.ReviewAndFinish;
import org.witness.informa.Tagger;
import org.witness.informa.utils.InformaConstants;
import org.witness.informa.utils.InformaConstants.Keys;
import org.witness.ssc.image.detect.GoogleFaceDetection;
import org.witness.ssc.image.filters.RegionProcesser;
import org.witness.ssc.utils.ObscuraConstants;
import org.witness.ssc.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageEditor extends SherlockActivity implements OnTouchListener, OnClickListener {
	// Image Matrix
	Matrix matrix = new Matrix();

	// Saved Matrix for not allowing a current operation (over max zoom)
	Matrix savedMatrix = new Matrix();
		
	// We can be in one of these 3 states
	int mode = ObscuraConstants.NONE;
	
	// For Zooming
	float startFingerSpacing = 0f;
	float endFingerSpacing = 0f;
	PointF startFingerSpacingMidPoint = new PointF();
	
	// For Dragging
	PointF startPoint = new PointF();
	
	// Don't allow it to move until the finger moves more than this amount
	// Later in the code, the minMoveDistance in real pixels is calculated
	// to account for different touch screen resolutions
	float minMoveDistanceDP = 5f;
	float minMoveDistance; // = ViewConfiguration.get(this).getScaledTouchSlop();
	
	// zoom in and zoom out buttons
	Button zoomIn, zoomOut, btnSave, btnShare, btnPreview, btnNew;
	
	// ImageView for the original (scaled) image
	ImageView imageView;
		
	// Bitmap for the original image (scaled)
	Bitmap imageBitmap;
	
	// Bitmap for holding the realtime obscured image
    Bitmap obscuredBmp;
    
    // Canvas for drawing the realtime obscuring
    Canvas obscuredCanvas;
	
    // Paint obscured
    Paint obscuredPaint;
    
    //bitmaps for corners
    private final static float CORNER_SIZE = 26;
    Bitmap bitmapCornerUL;
    Bitmap bitmapCornerUR;
    Bitmap bitmapCornerLL;
    Bitmap bitmapCornerLR;
    
	// Vector to hold ImageRegions
    ImageRegion currRegion = null;
    ImageRegion regionInContext = null;
	ArrayList<ImageRegion> imageRegions = new ArrayList<ImageRegion>(); 
	
	// The original image dimensions (not scaled)
	int originalImageWidth;
	int originalImageHeight;

	// So we can give some haptic feedback to the user
	Vibrator vibe;

	// Original Image Uri
	Uri originalImageUri;
	
	// sample sized used to downsize from native photo
	int inSampleSize;
	
	// Saved Image Uri
	Uri savedImageUri;
	
	//handles threaded events for the UI thread
    private Handler mHandler = new Handler();

    //UI for background threads
    ProgressDialog mProgressDialog;
    
    // Handles when we should do realtime preview and when we shouldn't
    boolean doRealtimePreview = true;
    
    // Keep track of the orientation
    private int originalImageOrientation = ExifInterface.ORIENTATION_NORMAL;
    
    ActionBar ab;
    Menu menu;
    boolean showHints;
    
    SharedPreferences sp;
    SharedPreferences.Editor ed;
    
    BroadcastReceiver br = new BroadcastReceiver() {
		@Override
		public void onReceive(Context c, Intent i) {
			if(InformaConstants.Keys.Service.FINISH_ACTIVITY.equals(i.getAction())) {
				try {
					reviewAndFinish();
				} catch(NullPointerException e) {
					Toast.makeText(ImageEditor.this, "There was an error creating your image.  Please try again.", Toast.LENGTH_LONG).show();
					finish();
				}
			}
		}
    };
    
    private void reviewAndFinish() {
    	mProgressDialog.cancel();
    	Intent i = new Intent(this, ReviewAndFinish.class);
    	i.setData(savedImageUri);
    	i.putExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO);
    	startActivityForResult(i, ObscuraConstants.REVIEW_MEDIA);
    	finish();
    }
    
	private class mAutoDetectTask extends AsyncTask<Integer, Integer, Long> {
		protected Long doInBackground(Integer... params) {
	    	  return (long)doAutoDetection();	         
	    }

	    protected void onProgressUpdate(Integer... progress) {
	       
	    }

	    protected void onPostExecute(Long result) {
	     
	    	mProgressDialog.dismiss();
	    	 
	    	Toast autodetectedToast = Toast.makeText(ImageEditor.this, result + " face(s) detected", Toast.LENGTH_SHORT);
	 		autodetectedToast.show();
	    }
	}
    		
	@SuppressWarnings({ "unused", "deprecation" })
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock_Light);
		
		super.onCreate(savedInstanceState);
		
		ab = getSupportActionBar();
		ab.setDisplayShowHomeEnabled(false);
		ab.setDisplayShowTitleEnabled(false);
		
		Log.d(InformaConstants.TAG, "ON CREATE CALLED");
		
		sendBroadcast(new Intent()
			.setAction(InformaConstants.Keys.Service.SET_CURRENT)
			.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, System.currentTimeMillis())
			.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.MEDIA_CAPTURED));
		
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        
        if(sp.getString(Keys.Settings.HAS_DB_PASSWORD, InformaConstants.PW_EXPIRY).compareTo(InformaConstants.PW_EXPIRY) == 0)
        	finish();
        
        ed = sp.edit();
        showHints = sp.getBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, true);
        
		setContentView(R.layout.imageviewer);

		// Calculate the minimum distance
		minMoveDistance = minMoveDistanceDP * this.getResources().getDisplayMetrics().density + 0.5f;
		
		// The ImageView that contains the image we are working with
		imageView = (ImageView) findViewById(R.id.ImageEditorImageView);

		// Buttons for zooming
		zoomIn = (Button) this.findViewById(R.id.ZoomIn);
		zoomOut = (Button) this.findViewById(R.id.ZoomOut);
		zoomIn.setOnClickListener(this);
		zoomOut.setOnClickListener(this);

		// Instantiate the vibrator
		vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		
		// Passed in from CameraObscuraMainMenu
		originalImageUri = getIntent().getData();
		
		Log.d(InformaConstants.TAG, "created file: " + originalImageUri.toString());
		
		// If originalImageUri is null, we are likely coming from another app via "share"
		if (originalImageUri == null)
		{
			if (getIntent().hasExtra(Intent.EXTRA_STREAM)) 
			{
				originalImageUri = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
			}
			else if (getIntent().hasExtra("bitmap"))
			{
				Bitmap b = (Bitmap)getIntent().getExtras().get("bitmap");
				setBitmap(b);
				
				boolean autodetect = true;

				if (autodetect)
				{

					mProgressDialog = ProgressDialog.show(this, "", "Detecting faces...", true, true);
					
					new mAutoDetectTask().execute(1);
					
					
				}
				
				originalImageWidth = b.getWidth();
				originalImageHeight = b.getHeight();
				return;
				
			}
		}
		
		
		// Load the image if it isn't null
		if (originalImageUri != null) {
			
			// Get the orientation
			File originalFilename = pullPathFromUri(originalImageUri);
			Log.d(InformaConstants.TAG, "pulling path: " + originalFilename.getAbsolutePath());
			try {
				JSONObject exif = new JSONObject();
				ExifInterface ei = new ExifInterface(originalFilename.getAbsolutePath());
				
				originalImageOrientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
				
				exif.put(Keys.Exif.APERTURE, ei.getAttribute(Keys.Exif.APERTURE));
				exif.put(Keys.Exif.EXPOSURE, ei.getAttribute(Keys.Exif.EXPOSURE));
				exif.put(Keys.Exif.FLASH, ei.getAttributeInt(Keys.Exif.FLASH, InformaConstants.NOT_REPORTED));
				exif.put(Keys.Exif.FOCAL_LENGTH, ei.getAttributeInt(Keys.Exif.FOCAL_LENGTH, InformaConstants.NOT_REPORTED));
				exif.put(Keys.Exif.IMAGE_LENGTH, ei.getAttributeInt(Keys.Exif.IMAGE_LENGTH, InformaConstants.NOT_REPORTED));
				exif.put(Keys.Exif.IMAGE_WIDTH, ei.getAttributeInt(Keys.Exif.IMAGE_WIDTH, InformaConstants.NOT_REPORTED));
				exif.put(Keys.Exif.ISO, ei.getAttribute(Keys.Exif.ISO));
				exif.put(Keys.Exif.MAKE, ei.getAttribute(Keys.Exif.MAKE));
				exif.put(Keys.Exif.MODEL, ei.getAttribute(Keys.Exif.MODEL));
				exif.put(Keys.Exif.ORIENTATION, originalImageOrientation);
				exif.put(Keys.Exif.WHITE_BALANCE, ei.getAttributeInt(Keys.Exif.WHITE_BALANCE, InformaConstants.NOT_REPORTED));
				
				sendBroadcast(new Intent()
				.setAction(InformaConstants.Keys.Service.SET_EXIF)
				.putExtra(Keys.Image.EXIF, exif.toString()));
				
				debug(ObscuraConstants.TAG,"EXIF: " + exif.toString());
			} catch (IOException e1) {
				debug(ObscuraConstants.TAG,"Couldn't get Orientation");
				e1.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}

			// Load up smaller image
			try {
				// Load up the image's dimensions not the image itself
				BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
				bmpFactoryOptions.inJustDecodeBounds = true;
				// Needs to be this config for Google Face Detection 
				bmpFactoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
				// Parse the image
				Bitmap loadedBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(originalImageUri), null, bmpFactoryOptions);

				// Hold onto the unscaled dimensions
				originalImageWidth = bmpFactoryOptions.outWidth;
				originalImageHeight = bmpFactoryOptions.outHeight;
				// If it is rotated, transpose the width and height
				// Should probably look to see if there are different rotation constants being used
				if (originalImageOrientation == ExifInterface.ORIENTATION_ROTATE_90 
						|| originalImageOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
					int tmpWidth = originalImageWidth;
					originalImageWidth = originalImageHeight;
					originalImageHeight = tmpWidth;
				}

				// Get the current display to calculate ratios
				Display currentDisplay = getWindowManager().getDefaultDisplay();
				
				// Ratios between the display and the image
				double widthRatio =  Math.floor(bmpFactoryOptions.outWidth / currentDisplay.getWidth());
				double heightRatio = Math.floor(bmpFactoryOptions.outHeight / currentDisplay.getHeight());

				
				// If both of the ratios are greater than 1,
				// one of the sides of the image is greater than the screen
				if (heightRatio > 1 && widthRatio > 1) {
					if (heightRatio > widthRatio) {
						// Height ratio is larger, scale according to it
						inSampleSize = (int)heightRatio;
					} else {
						// Width ratio is larger, scale according to it
						inSampleSize = (int)widthRatio;
					}
				}
				else
				{
					inSampleSize = 1;
				}
				
				bmpFactoryOptions.inSampleSize = inSampleSize;
		
				// Decode it for real
				bmpFactoryOptions.inJustDecodeBounds = false;
				loadedBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(originalImageUri), null, bmpFactoryOptions);
				debug(ObscuraConstants.TAG,"Was: " + loadedBitmap.getConfig());

				if (loadedBitmap == null) {
					debug(ObscuraConstants.TAG,"bmp is null");
				
				}
				else
				{
					// Only dealing with 90 and 270 degree rotations, might need to check for others
					if (originalImageOrientation == ExifInterface.ORIENTATION_ROTATE_90) 
					{
						debug(ObscuraConstants.TAG,"Rotating Bitmap 90");
						Matrix rotateMatrix = new Matrix();
						rotateMatrix.postRotate(90);
						loadedBitmap = Bitmap.createBitmap(loadedBitmap,0,0,loadedBitmap.getWidth(),loadedBitmap.getHeight(),rotateMatrix,false);
					}
					else if (originalImageOrientation == ExifInterface.ORIENTATION_ROTATE_270) 
					{
						debug(ObscuraConstants.TAG,"Rotating Bitmap 270");
						Matrix rotateMatrix = new Matrix();
						rotateMatrix.postRotate(270);
						loadedBitmap = Bitmap.createBitmap(loadedBitmap,0,0,loadedBitmap.getWidth(),loadedBitmap.getHeight(),rotateMatrix,false);
					}

					setBitmap (loadedBitmap);
					
					boolean autodetect = true;

					if (autodetect)
					{
						// Do auto detect popup

						mProgressDialog = ProgressDialog.show(this, "", "Detecting faces...", true, true);
					
						new mAutoDetectTask().execute(1);
					}
				}				
			} catch (IOException e) {
				Log.e(ObscuraConstants.TAG, "error loading bitmap from Uri: " + e.getMessage(), e);
			}
			
			
			
		}
		
		bitmapCornerUL = BitmapFactory.decodeResource(getResources(),
                R.drawable.edit_region_corner_ul);
		bitmapCornerUR = BitmapFactory.decodeResource(getResources(),
                R.drawable.edit_region_corner_ur);
		bitmapCornerLL = BitmapFactory.decodeResource(getResources(),
                R.drawable.edit_region_corner_ll);
		bitmapCornerLR = BitmapFactory.decodeResource(getResources(),
                R.drawable.edit_region_corner_lr);
		 
	}
	
	@Override
	public void onResume() {
		super.onResume();
		registerReceiver(br, new IntentFilter(Keys.Service.FINISH_ACTIVITY));
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(br);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@SuppressWarnings("deprecation")
	private void setBitmap (Bitmap nBitmap)
	{
		imageBitmap = nBitmap;
		
		// Get the current display to calculate ratios
		Display currentDisplay = getWindowManager().getDefaultDisplay();

		float matrixWidthRatio = (float) currentDisplay.getWidth() / (float) imageBitmap.getWidth();
		float matrixHeightRatio = (float) currentDisplay.getHeight() / (float) imageBitmap.getHeight();

		// Setup the imageView and matrix for scaling
		float matrixScale = matrixHeightRatio;
		
		if (matrixWidthRatio < matrixHeightRatio) {
			matrixScale = matrixWidthRatio;
		} 
		
		imageView.setImageBitmap(imageBitmap);

		// Set the OnTouch and OnLongClick listeners to this (ImageEditor)
		imageView.setOnTouchListener(this);
		imageView.setOnClickListener(this);
		
		
		//PointF midpoint = new PointF((float)imageBitmap.getWidth()/2f, (float)imageBitmap.getHeight()/2f);
		matrix.postScale(matrixScale, matrixScale);

		// This doesn't completely center the image but it get's closer
		//int fudge = 42;
		matrix.postTranslate((float)((float)currentDisplay.getWidth()-(float)imageBitmap.getWidth()*(float)matrixScale)/2f,(float)((float)currentDisplay.getHeight()-(float)imageBitmap.getHeight()*matrixScale)/2f);
		
		imageView.setImageMatrix(matrix);
		
		
	}
	
	
	private int doAutoDetection() {
		// This should be called via a pop-up/alert mechanism
		
		RectF[] autodetectedRects = runFaceDetection();
		for (int adr = 0; adr < autodetectedRects.length; adr++) {

			//debug(ObscuraApp.TAG,"AUTODETECTED imageView Width, Height: " + imageView.getWidth() + " " + imageView.getHeight());
			//debug(ObscuraApp.TAG,"UNSCALED RECT:" + autodetectedRects[adr].left + " " + autodetectedRects[adr].top + " " + autodetectedRects[adr].right + " " + autodetectedRects[adr].bottom);
			
			RectF autodetectedRectScaled = new RectF(autodetectedRects[adr].left, autodetectedRects[adr].top, autodetectedRects[adr].right, autodetectedRects[adr].bottom);
			
			//debug(ObscuraApp.TAG,"SCALED RECT:" + autodetectedRectScaled.left + " " + autodetectedRectScaled.top + " " + autodetectedRectScaled.right + " " + autodetectedRectScaled.bottom);

			// Probably need to map autodetectedRects to scaled rects
		//debug(ObscuraApp.TAG,"MAPPED RECT:" + autodetectedRects[adr].left + " " + autodetectedRects[adr].top + " " + autodetectedRects[adr].right + " " + autodetectedRects[adr].bottom);
			
			float faceBuffer = (autodetectedRectScaled.right-autodetectedRectScaled.left)/5;
			
			boolean isLast = false;
			if (adr == autodetectedRects.length - 1) {
				isLast = true;
			}
			createImageRegion(
					(autodetectedRectScaled.left-faceBuffer),
					(autodetectedRectScaled.top-faceBuffer),
					(autodetectedRectScaled.right+faceBuffer),
					(autodetectedRectScaled.bottom+faceBuffer),
					isLast,
					isLast);
		}	 				
		
		return autodetectedRects.length;
	}
	
	/*
	 * The actual face detection calling method
	 */
	private RectF[] runFaceDetection() {
		RectF[] possibleFaceRects;
		
		try {
			Bitmap bProc = toGrayscale(imageBitmap);
			GoogleFaceDetection gfd = new GoogleFaceDetection(bProc);
			int numFaces = gfd.findFaces();
	        debug(ObscuraConstants.TAG,"Num Faces Found: " + numFaces); 
	        possibleFaceRects = gfd.getFaces();
		} catch(NullPointerException e) {
			possibleFaceRects = null;
		}
		return possibleFaceRects;				
	}
	
	public Bitmap toGrayscale(Bitmap bmpOriginal)
	{        
	    int width, height;
	    height = bmpOriginal.getHeight();
	    width = bmpOriginal.getWidth();    

	    Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	    Canvas c = new Canvas(bmpGrayscale);
	    Paint paint = new Paint();
	    ColorMatrix cm = new ColorMatrix();
	    cm.setSaturation(0);
	    
	    ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
	    
	    paint.setColorFilter(f);
	 
	    c.drawBitmap(bmpOriginal, 0, 0, paint);
	    
	    
	    
	    return bmpGrayscale;
	}
	
	public static Bitmap createContrast(Bitmap src, double value) {
		// image size
		int width = src.getWidth();
		int height = src.getHeight();
		// create output bitmap
		Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());
		// color information
		int A, R, G, B;
		int pixel;
		// get contrast value
		double contrast = Math.pow((100 + value) / 100, 2);

		// scan through all pixels
		for(int x = 0; x < width; ++x) {
			for(int y = 0; y < height; ++y) {
				// get pixel color
				pixel = src.getPixel(x, y);
				A = Color.alpha(pixel);
				// apply filter contrast for every channel R, G, B
				R = Color.red(pixel);
				R = (int)(((((R / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(R < 0) { R = 0; }
				else if(R > 255) { R = 255; }

				G = Color.red(pixel);
				G = (int)(((((G / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(G < 0) { G = 0; }
				else if(G > 255) { G = 255; }

				B = Color.red(pixel);
				B = (int)(((((B / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
				if(B < 0) { B = 0; }
				else if(B > 255) { B = 255; }

				// set new pixel color to output bitmap
				bmOut.setPixel(x, y, Color.argb(A, R, G, B));
			}
		}

		// return final image
		return bmOut;
	}

	/*
	 * Handles touches on ImageView
	 */
	@Override
	public boolean onTouch(View v, MotionEvent event) 
	{
		if (currRegion != null && (mode == ObscuraConstants.DRAG || currRegion.getBounds().contains(event.getX(), event.getY())))		
			return onTouchRegion(v, event, currRegion);	
		else
			return onTouchImage(v,event);
	}
	
	public ImageRegion findRegion (MotionEvent event)
	{
		ImageRegion result = null;
		Matrix iMatrix = new Matrix();
		matrix.invert(iMatrix);

		float[] points = {event.getX(), event.getY()};        	
    	iMatrix.mapPoints(points);
    	
		for (ImageRegion region : imageRegions)
		{

			if (region.getBounds().contains(points[0],points[1]))
			{
				result = region;
				
				break;
			}
			
		}
	
		
		return result;
	}
	
	public boolean onTouchRegion (View v, MotionEvent event, ImageRegion iRegion)
	{
		boolean handled = false;
		
		currRegion.setMatrix(matrix);
		
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				clearImageRegionsEditMode();
				currRegion.setSelected(true);
				
				currRegion.setCornerMode(event.getX(),event.getY());
				currRegion.setActive(true);
				
				regionInContext = currRegion;
				toggleRegionMenu();
				
				mode = ObscuraConstants.DRAG;
				handled = iRegion.onTouch(v, event);
			break;
			
			case MotionEvent.ACTION_UP:
				mode = ObscuraConstants.NONE;
				handled = iRegion.onTouch(v, event);
				currRegion.setSelected(false);
			
			break;
			
			default:
				mode = ObscuraConstants.DRAG;
				handled = iRegion.onTouch(v, event);
			
		}
		
		return handled;
		
		
	}
	
	public boolean onTouchImage(View v, MotionEvent event) 
	{
		boolean handled = false;
		
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				// Single Finger down
				regionInContext = null;
				toggleRegionMenu();
				
				mode = ObscuraConstants.TAP;				
				ImageRegion newRegion = findRegion(event);
				
				if (newRegion != null)
				{
					currRegion = newRegion;
					return onTouchRegion(v,  event, currRegion);
				}
				else if (currRegion == null)
				{
					
					// 	Save the Start point. 
					startPoint.set(event.getX(), event.getY());
				}
				else
				{
					currRegion.setSelected(false);
					currRegion = null;

				}
				
				break;
				
			case MotionEvent.ACTION_POINTER_DOWN:
				// Two Fingers down

				// Don't do realtime preview while touching screen
				//doRealtimePreview = false;
				//updateDisplayImage();

				// Get the spacing of the fingers, 2 fingers
				float sx = event.getX(0) - event.getX(1);
				float sy = event.getY(0) - event.getY(1);
				startFingerSpacing = (float) Math.sqrt(sx * sx + sy * sy);

				//Log.d(ObscuraApp.TAG, "Start Finger Spacing=" + startFingerSpacing);
				
				// Get the midpoint
				float xsum = event.getX(0) + event.getX(1);
				float ysum = event.getY(0) + event.getY(1);
				startFingerSpacingMidPoint.set(xsum / 2, ysum / 2);
				
				mode = ObscuraConstants.ZOOM;
				//Log.d(ObscuraApp.TAG, "mode=ZOOM");
				
				clearImageRegionsEditMode();
				
				break;
				
			case MotionEvent.ACTION_UP:
				// Single Finger Up
				
			    // Re-enable realtime preview
				doRealtimePreview = true;
				updateDisplayImage();
				
				//debug(ObscuraApp.TAG,"mode=NONE");

				break;
				
			case MotionEvent.ACTION_POINTER_UP:
				// Multiple Finger Up
				
			    // Re-enable realtime preview
				doRealtimePreview = true;
				updateDisplayImage();
				
				//Log.d(ObscuraApp.TAG, "mode=NONE");
				break;
				
			case MotionEvent.ACTION_MOVE:
				
				// Calculate distance moved
				float distance = (float) (Math.sqrt(Math.abs(startPoint.x - event.getX()) + Math.abs(startPoint.y - event.getY())));
				//debug(ObscuraApp.TAG,"Move Distance: " + distance);
				//debug(ObscuraApp.TAG,"Min Distance: " + minMoveDistance);
				
				// If greater than minMoveDistance, it is likely a drag or zoom
				if (distance > minMoveDistance) {
				
					if (mode == ObscuraConstants.TAP || mode == ObscuraConstants.DRAG) {
						mode = ObscuraConstants.DRAG;
						
						matrix.postTranslate(event.getX() - startPoint.x, event.getY() - startPoint.y);
						imageView.setImageMatrix(matrix);
//						// Reset the start point
						startPoint.set(event.getX(), event.getY());
	
						putOnScreen();
						//redrawRegions();
						
						handled = true;
	
					} else if (mode == ObscuraConstants.ZOOM) {
						
						// Save the current matrix so that if zoom goes to big, we can revert
						savedMatrix.set(matrix);
						
						// Get the spacing of the fingers, 2 fingers
						float ex = event.getX(0) - event.getX(1);
						float ey = event.getY(0) - event.getY(1);
						endFingerSpacing = (float) Math.sqrt(ex * ex + ey * ey);
	
						//Log.d(ObscuraApp.TAG, "End Finger Spacing=" + endFingerSpacing);
		
						// If we moved far enough
						if (endFingerSpacing > minMoveDistance) {
							
							// Ratio of spacing..  If it was 5 and goes to 10 the image is 2x as big
							float scale = endFingerSpacing / startFingerSpacing;
							// Scale from the midpoint
							matrix.postScale(scale, scale, startFingerSpacingMidPoint.x, startFingerSpacingMidPoint.y);
		
							// Make sure that the matrix isn't bigger than max scale/zoom
							float[] matrixValues = new float[9];
							matrix.getValues(matrixValues);
							
							if (matrixValues[0] > ObscuraConstants.MAX_SCALE) {
								matrix.set(savedMatrix);
							}
							imageView.setImageMatrix(matrix);
							
							putOnScreen();
							//redrawRegions();
	
							// Reset Start Finger Spacing
							float esx = event.getX(0) - event.getX(1);
							float esy = event.getY(0) - event.getY(1);
							startFingerSpacing = (float) Math.sqrt(esx * esx + esy * esy);
							//Log.d(ObscuraApp.TAG, "New Start Finger Spacing=" + startFingerSpacing);
							
							// Reset the midpoint
							float x_sum = event.getX(0) + event.getX(1);
							float y_sum = event.getY(0) + event.getY(1);
							startFingerSpacingMidPoint.set(x_sum / 2, y_sum / 2);
							
							handled = true;
						}
					}
				}
				break;
		}


		return handled; // indicate event was handled
	}
	
	
	private void toggleRegionMenu() {
		if(regionInContext != null)
			menu.getItem(0).setVisible(true);
		else
			menu.getItem(0).setVisible(false);
	}
	/*
	 * For live previews
	 */	
	public void updateDisplayImage()
	{
		if (doRealtimePreview) {
			imageView.setImageBitmap(createObscuredBitmap(imageBitmap.getWidth(),imageBitmap.getHeight(), true));
		} else {
			imageView.setImageBitmap(imageBitmap);
		}
		
		toggleRegionMenu();
	}
	
	/*
	 * Move the image onto the screen if it has been moved off
	 */
	public void putOnScreen() 
	{
		// Get Rectangle of Tranformed Image
		RectF theRect = getScaleOfImage();
		
		debug(ObscuraConstants.TAG,theRect.width() + " " + theRect.height());
		
		float deltaX = 0, deltaY = 0;
		if (theRect.width() < imageView.getWidth()) {
			deltaX = (imageView.getWidth() - theRect.width())/2 - theRect.left;
		} else if (theRect.left > 0) {
			deltaX = -theRect.left;
		} else if (theRect.right < imageView.getWidth()) {
			deltaX = imageView.getWidth() - theRect.right;
		}		
		
		if (theRect.height() < imageView.getHeight()) {
			deltaY = (imageView.getHeight() - theRect.height())/2 - theRect.top;
		} else if (theRect.top > 0) {
			deltaY = -theRect.top;
		} else if (theRect.bottom < imageView.getHeight()) {
			deltaY = imageView.getHeight() - theRect.bottom;
		}
		
		//debug(ObscuraApp.TAG,"Deltas:" + deltaX + " " + deltaY);
		
		matrix.postTranslate(deltaX,deltaY);
		imageView.setImageMatrix(matrix);
		updateDisplayImage();
		
	}
	
	/* 
	 * Put all regions into normal mode, out of edit mode
	 */
	public void clearImageRegionsEditMode()
	{
		Iterator<ImageRegion> itRegions = imageRegions.iterator();
		
		while (itRegions.hasNext())
		{
			ImageRegion ir = itRegions.next();
			ir.setSelected(false);
			ir.setActive(false);
		}
		
	}
	
	/*
	 * Create new ImageRegion
	 */
	public void createImageRegion(float left, float top, float right, float bottom, boolean showPopup, boolean updateNow) {
		vibe.vibrate(100);
		clearImageRegionsEditMode();
		
		ImageRegion imageRegion = new ImageRegion(
				this, 
				left, 
				top, 
				right, 
				bottom,
				matrix);

		imageRegions.add(imageRegion);
		
		if (updateNow)
		{
			mHandler.post(new Runnable ()
			{
				public void run() {
					putOnScreen();
				}
			});
		}
	}
	/*
	 * Associate the current log data to the image region's properties
	 */
	public void associateImageRegionData(ImageRegion ir) {
		sendBroadcast(new Intent()
			.setAction(InformaConstants.Keys.Service.SET_CURRENT)
			.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, (Long) ir.mRProc.getProperties().get(InformaConstants.Keys.ImageRegion.TIMESTAMP))
			.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.REGION_GENERATED));
	}
	
	/*
	 * Delete/Remove specific ImageRegion
	 */
	public void deleteRegion(ImageRegion ir)
	{
		imageRegions.remove(ir);
		//redrawRegions();
		updateDisplayImage();
	}
	
	/*
	 * Returns the Rectangle of Tranformed Image
	 */
	public RectF getScaleOfImage() 
	{
		RectF theRect = new RectF(0,0,imageBitmap.getWidth(), imageBitmap.getHeight());
		matrix.mapRect(theRect);
		return theRect;
	}

	
	/*
	 * Handles normal onClicks for buttons registered to this.
	 * Currently only the zoomIn and zoomOut buttons
	 */
	@Override
	public void onClick(View v) {
		
		if (currRegion != null)
		{
			currRegion = null;
		}			
		else if (v == zoomIn) 
		{
			float scale = 1.5f;
			
			PointF midpoint = new PointF(imageView.getWidth()/2, imageView.getHeight()/2);
			matrix.postScale(scale, scale, midpoint.x, midpoint.y);
			imageView.setImageMatrix(matrix);
			putOnScreen();
		} 
		else if (v == zoomOut) 
		{
			float scale = 0.75f;

			PointF midpoint = new PointF(imageView.getWidth()/2, imageView.getHeight()/2);
			matrix.postScale(scale, scale, midpoint.x, midpoint.y);
			imageView.setImageMatrix(matrix);
			
			putOnScreen();
		} 
		else if (mode != ObscuraConstants.DRAG && mode != ObscuraConstants.ZOOM) 
		{
			float defaultSize = imageView.getWidth()/4;
			float halfSize = defaultSize/2;
			
			RectF newBox = new RectF();
			
			newBox.left = startPoint.x - halfSize;
			newBox.top = startPoint.y - halfSize;

			newBox.right = startPoint.x + halfSize;
			newBox.bottom = startPoint.y + halfSize;
			
			Matrix iMatrix = new Matrix();
			matrix.invert(iMatrix);
			iMatrix.mapRect(newBox);
						
			createImageRegion(newBox.left, newBox.top, newBox.right, newBox.bottom, true, true);
		}
		
	}
	/*
	 * Standard method for menu items.  Uses res/menu/image_editor_menu.xml
	 */
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater mi = getSupportMenuInflater();
        mi.inflate(R.menu.image_editor_menu, menu);
        
        menu.getItem(0).setVisible(false);
        this.menu = menu;
        
        return super.onCreateOptionsMenu(menu);
    }

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {

    	switch (item.getItemId()) {
    		case R.id.menu_current_region_redact:
    			if(regionInContext != null) {
    				regionInContext.updateRegionProcessor(ImageRegion.REDACT);
    				return true;
    			}	
    			return false;
    		case R.id.menu_current_region_pixelate:
    			if(regionInContext != null) {
    				regionInContext.updateRegionProcessor(ImageRegion.PIXELATE);
    				return true;
    			}
    			return false;
    		case R.id.menu_current_region_crowd_pixelate:
    			if(regionInContext != null) {
    				regionInContext.updateRegionProcessor(ImageRegion.BG_PIXELATE);
    				return true;
    			}
    			return false;
    		case R.id.menu_current_region_identify:
    			if(regionInContext != null) {
    				Log.d(InformaConstants.TAG, "region is not null");
    				regionInContext.updateRegionProcessor(ImageRegion.CONSENT);
    				return true;
    			}
    			return false;
    		case R.id.menu_current_region_delete:
    			if(regionInContext != null) {
    				deleteRegion(regionInContext);
    				regionInContext = null;
    				return true;
    			}
    			return false;
        	case R.id.menu_save:
				Intent keyChooser = new Intent(this, KeyChooser.class);
				startActivityForResult(keyChooser, InformaConstants.FROM_TRUSTED_DESTINATION_CHOOSER);
        		return true;
        	case R.id.menu_preview:
        		showPreview();
        		return true;
        	case R.id.menu_hide_hints:
        		if(sp.getBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, true)) {
        			ed.putBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, false).commit();
        			showHints = false;
        		} else {
        			ed.putBoolean(ObscuraConstants.Preferences.Keys.SHOW_HINTS, true).commit();
        			showHints = true;
        		}
        		
    		default:
    			return false;
    	}
    }
	
	/*
	 * Display preview image
	 */
	private void showPreview() {
		
		// Open Preview Activity
		Uri tmpImageUri = saveTmpImage();
		
		if (tmpImageUri != null)
		{
			Intent intent = new Intent(this, ImagePreview.class);
			intent.putExtra(ImagePreview.IMAGEURI, tmpImageUri.toString());
			startActivity(intent);				
		}
	}
    
    
    /*
     * Goes through the regions that have been defined and creates a bitmap with them obscured.
     * This may introduce memory issues and therefore have to be done in a different manner.
     */
    private Bitmap createObscuredBitmap(int width, int height, boolean showBorders) 
    {
    	if (imageBitmap == null)
    		return null;
    	
    	if (obscuredBmp == null || (obscuredBmp.getWidth() != width))
    	{
    		// Create the bitmap that we'll output from this method
    		obscuredBmp = Bitmap.createBitmap(width, height,imageBitmap.getConfig());
    	
    		// Create the canvas to draw on
    		obscuredCanvas = new Canvas(obscuredBmp); 
    	}
    	
    	// Create the paint used to draw with
    	obscuredPaint = new Paint();   
    	// Create a default matrix
    	Matrix obscuredMatrix = new Matrix();    	
    	// Draw the scaled image on the new bitmap
    	obscuredCanvas.drawBitmap(imageBitmap, obscuredMatrix, obscuredPaint);
    	
    	// Iterate through the regions that have been created
    	Iterator<ImageRegion> i = imageRegions.iterator();
	    while (i.hasNext()) 
	    {
	    	
	    	ImageRegion currentRegion = i.next();
	    	
	    	RegionProcesser om = currentRegion.getRegionProcessor();

            RectF regionRect = new RectF(currentRegion.getBounds());
	    	om.processRegion(regionRect, obscuredCanvas, obscuredBmp);

	    	if (showBorders)
	    	{
	    		if (currentRegion.isSelected() || currentRegion.equals(regionInContext))
		    		obscuredPaint.setColor(Color.GREEN);
		    	else
		    		obscuredPaint.setColor(Color.WHITE);
		    	
		    	obscuredPaint.setStyle(Style.STROKE);
		    	obscuredPaint.setStrokeWidth(10f);
		    	obscuredCanvas.drawRect(regionRect, obscuredPaint);
		    	
		    	float cSize = CORNER_SIZE;
		    	
		    	if (currentRegion.isSelected() || currentRegion.equals(regionInContext))
		    	{
		    		obscuredCanvas.drawBitmap(bitmapCornerUL, regionRect.left-cSize, regionRect.top-cSize, obscuredPaint);
		    		obscuredCanvas.drawBitmap(bitmapCornerLL, regionRect.left-cSize, regionRect.bottom-(cSize/2), obscuredPaint);
		    		obscuredCanvas.drawBitmap(bitmapCornerUR, regionRect.right-(cSize/2), regionRect.top-cSize, obscuredPaint);
		    		obscuredCanvas.drawBitmap(bitmapCornerLR, regionRect.right-(cSize/2), regionRect.bottom-(cSize/2), obscuredPaint);

		    	}
		    	
	    	}
		}

	    return obscuredBmp;
    }
    
    /*
     * Save a temporary image for sharing only
     */
    private Uri saveTmpImage() {
    	
    	String storageState = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(storageState)) {
        	Toast t = Toast.makeText(this,"External storage not available", Toast.LENGTH_SHORT); 
    		t.show();
    		return null;
    	}
    	
    	// Create the bitmap that will be saved
    	// Perhaps this should be smaller than screen size??
    	int w = imageBitmap.getWidth();
    	int h = imageBitmap.getHeight();
    	Bitmap obscuredBmp = createObscuredBitmap(w,h, false);
    	
    	// Create the Uri - This can't be "private"
    	File tmpFileDirectory = new File(ObscuraConstants.TMP_FILE_DIRECTORY);
    	File tmpFile = new File(tmpFileDirectory, ObscuraConstants.TMP_FILE_NAME_IMAGE);
    	debug(ObscuraConstants.TAG, tmpFile.getPath());
    	
		try {
	    	if (!tmpFileDirectory.exists()) {
	    		tmpFileDirectory.mkdirs();
	    	}
	    	Uri tmpImageUri = Uri.fromFile(tmpFile);
	    	
			OutputStream imageFileOS;

			int quality = 75;
			imageFileOS = getContentResolver().openOutputStream(tmpImageUri);
			obscuredBmp.compress(CompressFormat.JPEG, quality, imageFileOS);

			return tmpImageUri;
		} catch (FileNotFoundException e) {
			mProgressDialog.cancel();
			e.printStackTrace();
			return null;
		}
    }
    
    /*
     * The method that actually saves the altered image.  
     * This in combination with createObscuredBitmap could/should be done in another, more memory efficient manner. 
     */
    
    private boolean saveImage(long[] encryptList) throws IOException {
    	SimpleDateFormat dateFormat = new SimpleDateFormat(ObscuraConstants.EXPORT_DATE_FORMAT);
    	Date date = new Date();
    	String dateString = dateFormat.format(date);
    	
    	ContentValues cv = new ContentValues();
    	cv.put(Images.Media.DATE_ADDED, dateString);
    	cv.put(Images.Media.DATE_TAKEN, dateString);
    	cv.put(Images.Media.DATE_MODIFIED, dateString);
    	cv.put(Images.Media.DESCRIPTION, ObscuraConstants.ExifValues.DESCRIPTION);
    	cv.put(Images.Media.TITLE, ObscuraConstants.ExifValues.TITLE);
    	
    	savedImageUri = getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, cv);
    	obscuredBmp = createObscuredBitmap(imageBitmap.getWidth(),imageBitmap.getHeight(), false);

		OutputStream imageFileOS;
	
		int quality = 100; //lossless?  good question - still a smaller version
		imageFileOS = getContentResolver().openOutputStream(savedImageUri);
		obscuredBmp.compress(CompressFormat.JPEG, quality, imageFileOS);
		
		//TODO: handle the exif wiping...
		
		// force mediascanner to update file
		MediaScannerConnection.scanFile(
				this,
				new String[] {pullPathFromUri(savedImageUri).getAbsolutePath()},
				new String[] {ObscuraConstants.MIME_TYPE_JPEG},
				null);
		
		File tmp = new File(InformaConstants.DUMP_FOLDER, ObscuraConstants.TMP_FILE_NAME_IMAGE);
		if(tmp.exists())
			tmp.delete();
				
		try {
			FileOutputStream tmpImage = new FileOutputStream(tmp.getAbsoluteFile());
			imageBitmap.compress(CompressFormat.JPEG, quality, tmpImage);
		} catch(IOException e) {
			Log.d(InformaConstants.TAG, "error saving tmp bitmap: " + e);
			return false;
		}
		
		sendBroadcast(new Intent()
			.setAction(InformaConstants.Keys.Service.SET_CURRENT)
			.putExtra(InformaConstants.Keys.CaptureEvent.MATCH_TIMESTAMP, System.currentTimeMillis())
			.putExtra(InformaConstants.Keys.CaptureEvent.TYPE, InformaConstants.CaptureEvents.MEDIA_SAVED));
	
		JSONArray imageRegionObject = new JSONArray();
		try {
			for(ImageRegion ir : imageRegions)
				imageRegionObject.put(ir.getRepresentation());
		} catch (JSONException e) {
			Log.e(InformaConstants.TAG, "problem: " + e.toString());
			return false;
		}
		
    	Intent informa = new Intent()
			.setAction(InformaConstants.Keys.Service.SEAL_LOG)
			.putExtra(InformaConstants.Keys.Media.MEDIA_TYPE, InformaConstants.MediaTypes.PHOTO)
			.putExtra(InformaConstants.Keys.ImageRegion.DATA, imageRegionObject.toString())
			.putExtra(InformaConstants.Keys.Image.LOCAL_MEDIA_PATH, pullPathFromUri(savedImageUri).getAbsolutePath());
		
    	for(long l : encryptList)
    		Log.d(InformaConstants.TAG, "to key: " + l);
		if(encryptList[0] != 0)
			informa.putExtra(InformaConstants.Keys.Intent.ENCRYPT_LIST, encryptList);
		
    	sendBroadcast(informa);
		return true;
    }
    
    public File pullPathFromUri(Uri originalUri) {

    	String originalImageFilePath = null;

    	if (originalUri.getScheme() != null && originalUri.getScheme().equals("file"))
    	{
    		originalImageFilePath = originalUri.toString();
    	}
    	else
    	{
	    	String[] columnsToSelect = { MediaStore.Images.Media.DATA };
	    	Cursor imageCursor = getContentResolver().query(originalUri, columnsToSelect, null, null, null );
	    	if ( imageCursor != null && imageCursor.getCount() == 1 ) {
		        imageCursor.moveToFirst();
		        originalImageFilePath = imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA));
	    	}
    	}

    	return new File(originalImageFilePath);
    }
    
    /*
     * Handling screen configuration changes ourselves, we don't want the activity to restart on rotation
     */
    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
    
        Thread thread = new Thread ()
        {
        	public void run ()
        	{        
        		mHandler.postDelayed(new Runnable () { public void run () { putOnScreen();}},100);        		
        	}
        };
        
        
        thread.start();
    }    
    
    public void launchTagger(ImageRegion ir) {
    	Intent informa = new Intent(this, Tagger.class);
    	informa.putExtra(ObscuraConstants.ImageRegion.PROPERTIES, ir.getRegionProcessor().getProperties());
    	informa.putExtra(InformaConstants.Keys.ImageRegion.INDEX, imageRegions.indexOf(ir));
    	
    	ir.getRegionProcessor().processRegion(new RectF(ir.getBounds()), obscuredCanvas, obscuredBmp);
    	
    	if(ir.getRegionProcessor().getBitmap() != null) {
    		Bitmap b = ir.getRegionProcessor().getBitmap();
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		b.compress(Bitmap.CompressFormat.JPEG, 50, baos);
    		informa.putExtra(InformaConstants.Keys.ImageRegion.THUMBNAIL, baos.toByteArray());
    	}
    	
    	startActivityForResult(informa, InformaConstants.FROM_INFORMA_TAGGER);
    	
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if(resultCode == SherlockActivity.RESULT_OK) {
    		if(requestCode == InformaConstants.FROM_INFORMA_TAGGER) {
    			// replace corresponding image region
    			@SuppressWarnings("unchecked")
				HashMap<String, Object> informaReturn = 
					(HashMap<String, Object>) data.getSerializableExtra(InformaConstants.Keys.ImageRegion.TAGGER_RETURN);    			
    			Properties mProp = imageRegions.get(data.getIntExtra(InformaConstants.Keys.ImageRegion.INDEX, 0))
    					.getRegionProcessor().getProperties();
    			
    			// iterate through returned hashmap and place these new properties in it.
    			for(Map.Entry<String, Object> entry : informaReturn.entrySet())
    				mProp.setProperty(entry.getKey(), entry.getValue().toString());
    			
    			imageRegions.get(data.getIntExtra(InformaConstants.Keys.ImageRegion.INDEX, 0))
    				.getRegionProcessor().setProperties(mProp);
    			    			
    		} else if(requestCode == InformaConstants.FROM_TRUSTED_DESTINATION_CHOOSER) {
    			mProgressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.saving), true, true);
    			mHandler.postDelayed(new Runnable() {
    				  @Override
    				  public void run() {
    				    // this will be done in the Pipeline Thread
    					long[] encryptList = new long[] {0L};
		        		if(data.hasExtra(InformaConstants.Keys.Intent.ENCRYPT_LIST))
		        			encryptList = data.getLongArrayExtra(InformaConstants.Keys.Intent.ENCRYPT_LIST);
		        		try {
							saveImage(encryptList);
						} catch (IOException e) {
							Log.e(InformaConstants.TAG, "error saving image", e);
						}
    				  }
    				},500);
    		} else if(requestCode == ObscuraConstants.REVIEW_MEDIA) {
    			setResult(SherlockActivity.RESULT_OK);
    			finish();
    		}
    	}
    }

	@Override
	protected void onPostResume() {
		super.onPostResume();
	}
	
	public Paint getPainter ()
	{
		return obscuredPaint;
	}
	
	private void debug (String tag, String message)
	{
		Log.d(tag, message);
	}
	

	public ImageView getImageView() {
		return imageView;
	}
	
	@Override
	public void onAttachedToWindow() {
	    super.onAttachedToWindow();
	    Window window = getWindow();
	    window.setFormat(PixelFormat.RGBA_8888);
	    window.getDecorView().getBackground().setDither(true);

	}
}
