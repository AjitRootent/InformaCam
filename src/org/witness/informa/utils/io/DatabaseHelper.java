package org.witness.informa.utils.io;

import java.util.ArrayList;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.witness.informa.utils.InformaConstants.Keys.*;

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;

public class DatabaseHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "informa.db";
	private static final int DATABASE_VERSION = 1;
	
	public static String TABLE;
	
	public enum QueryBuilders {
		INIT_INFORMA() {
			@Override
			public String[] build() {
				return new String[] {
					"CREATE TABLE " + Tables.IMAGES + " (" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							Image.METADATA + " blob not null, " +
							Image.CONTAINMENT_ARRAY + " blob not null, " +
							Image.UNREDACTED_IMAGE_HASH + " text not null, " +
							Image.REDACTED_IMAGE_HASH + " text not null, " +
							Image.LOCATION_OF_ORIGINAL + " text not null, " +
							Image.LOCATION_OF_OBSCURED_VERSION + " text not null, " +
							Intent.Destination.EMAIL + " blob not null, " +
							Image.SHARED_SECRET + " text" +
							")",
					"CREATE TABLE " + Tables.CONTACTS + " (" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							ImageRegion.Subject.PSEUDONYM + " text not null, " +
							ImageRegion.Subject.PERSIST_FILTER + " integer not null" +
							")",
					"CREATE TABLE " + Tables.SETUP + "(" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							Owner.SIG_KEY_ID + " text, " +
							Owner.PRIMARY_EMAIL + " text not null, " +
							Device.IMAGE_FINGERPRINT + " blob not null, " +
							Owner.DEFAULT_SECURITY_LEVEL + " integer not null, " +
							Device.LOCAL_TIMESTAMP + " integer not null, " +
							Device.PUBLIC_TIMESTAMP + " integer not null, " +
							Owner.OWNERSHIP_TYPE + " integer not null" +
							")",
					"CREATE TABLE " + Tables.IMAGE_REGIONS + " (" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							ImageRegion.Data.UNREDACTED_HASH + " text not null, " +
							ImageRegion.DATA + " blob not null, " +
							ImageRegion.BASE + " text not null" +
							")",
					"CREATE TABLE " + Tables.TRUSTED_DESTINATIONS + " (" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							TrustedDestinations.EMAIL + " text not null, " +
							TrustedDestinations.PHONE_NUMBER + " text, " +
							TrustedDestinations.CONTACT_ID + " text not null, " +
							TrustedDestinations.KEYRING_ID + " text, " +
							TrustedDestinations.DISPLAY_NAME + " text not null," +
							TrustedDestinations.DESTO + " text" +
							")",
					"CREATE TABLE " + Tables.ENCRYPTED_IMAGES + " (" + BaseColumns._ID + " " +
							"integer primary key autoincrement, " +
							ImageRegion.BASE + " text not null, " +
							ImageRegion.DATA + " blob not null" +
							")"
				};
			}
		},
		
		CHECK_IF() {
			@Override
			public String[] build() {
				return new String[] {
					"SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name= '" + TABLE + "'" 
				};
			}
		};
		
		public abstract String[] build();
	}
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
	}

	@Override
	public void onCreate(SQLiteDatabase db) {}
	
	public void removeValue(SQLiteDatabase db, String[] matchKey, Object[] matchValue) {
		String query = "DELETE FROM " + TABLE + " WHERE ";
		for(int m=0; m<matchKey.length; m++) {
			if(matchValue[m].getClass().equals(String.class))
				matchValue[m] = "\"" + matchValue[m] + "\"";
			query += (matchKey[m] + "=" + matchValue[m] + " AND ");
		}
		query = query.substring(0, query.length() - 5);
		db.execSQL(query);
	}
	
	public Cursor getValue(SQLiteDatabase db, String[] values, String matchKey, Object matchValue) {
		String select = "*";
		
		if(values != null) {
			StringBuffer sb = new StringBuffer();
			for(String v : values)
				sb.append(v + ",");
			select = sb.toString().substring(0, sb.toString().length() - 1);
		}
		
		String query = "SELECT " + select + " FROM " + getTable();
		
		if(matchKey != null) {
			if(matchValue.getClass().equals(String.class))
				matchValue = "\"" + matchValue + "\"";
		
			query += " WHERE " + matchKey + " = " + matchValue;
		}
				
		Cursor c = db.rawQuery(query, null);
		
		if(c != null && c.getCount() > 0) {
			return c;
		} else
			return null;
	}
	
	public boolean setTable(SQLiteDatabase db, String whichTable) {
		TABLE = whichTable;
		Cursor c = db.rawQuery(QueryBuilders.CHECK_IF.build()[0], null);
		if(c != null && c.getCount() > 0) {
			c.close();
			return true;
		} else {
			c.close();
			ArrayList<String> queries = new ArrayList<String>();
			if(getTable().compareTo(Tables.IMAGES) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[0]);
			else if(getTable().compareTo(Tables.CONTACTS) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[1]);
			else if(getTable().compareTo(Tables.SETUP) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[2]);
			else if(getTable().compareTo(Tables.IMAGE_REGIONS) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[3]);
			else if(getTable().compareTo(Tables.TRUSTED_DESTINATIONS) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[4]);
			else if(getTable().compareTo(Tables.ENCRYPTED_IMAGES) == 0)
				queries.add(QueryBuilders.INIT_INFORMA.build()[5]);
			
			for(String q : queries)
				db.execSQL(q);
		}
		return false;
	}
	
	public String getTable() {
		return TABLE;
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if(oldVersion >= newVersion)
			return;
		
		String sql = null;
		if(oldVersion == 1)
			sql = "ALTER TABLE " + TABLE + " add note text;";
		if(oldVersion == 2)
			sql = "";
		
		if(sql != null)
			db.execSQL(sql);
		
	}

}