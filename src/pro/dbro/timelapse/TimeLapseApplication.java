package pro.dbro.timelapse;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class TimeLapseApplication extends Application {
	// id -> TimeLapse
	public HashMap<Integer,TimeLapse> time_lapse_map = new HashMap<Integer,TimeLapse>();
	public int nextTimeLapseId = 0;
	
	// SQL Selection Clause
	public final String selectionClause = SQLiteWrapper.COLUMN_ID + " = ?";
	
	// Singleton
	private static TimeLapseApplication instance;

    public TimeLapseApplication()
    {
        instance = this;
    }

    public static Context getContext()
    {
        return instance;
    }

	
	public void setTimeLapses(ArrayList<TimeLapse> list){
		// Transfer list items into map
		for(int x = 0; x < list.size(); x++){
			time_lapse_map.put(((TimeLapse)list.get(x)).id, list.get(x));
		}
		setNextTimeLapseId();
	}
	
	public void setTimeLapseTitleAndDescription(int timelapse_id, String title, String description){
		
		((TimeLapse)time_lapse_map.get(timelapse_id)).setTitleAndDescription(title, description);
	}
	
	public void createTimeLapse(String title, String description){
		time_lapse_map.put(nextTimeLapseId, new TimeLapse(title, description, nextTimeLapseId));
		Log.d("TimeLapseApplication","created TimeLapse " + String.valueOf(nextTimeLapseId));
		nextTimeLapseId ++;
		
	}
	
	private void setNextTimeLapseId(){
		Object[] keys = (Object[]) time_lapse_map.keySet().toArray();
		// find highest TimeLapse.id
		for(int x = 0; x < keys.length; x++){
			if(((TimeLapse)time_lapse_map.get(Integer.parseInt(keys[x].toString()))).id > nextTimeLapseId)
				nextTimeLapseId = ((TimeLapse)time_lapse_map.get(Integer.parseInt(keys[x].toString()))).id;
		}
		// add a 1 to it
		nextTimeLapseId++;
		Log.d("TimeLapseApplication","nextID: " + String.valueOf(nextTimeLapseId));
	}
	
	
	/**
	 * Content Resolver Wrapper methods
	 */
	
	public Cursor getTimeLapseById(int _id, String[] columns){
		// Query ContentResolver for related timelapse
        String[] selectionArgs = {String.valueOf(_id)};
        
        // If no columns provided, return all
        if(columns == null)
        	columns = SQLiteWrapper.COLUMNS;
       return getContentResolver().query(
        	    TimeLapseContentProvider.CONTENT_URI,  // The content URI of the words table
        	    columns,                // The columns to return for each row
        	    selectionClause,                    // Selection criteria
        	    selectionArgs,                     // Selection criteria
        	    null);                        	   // The sort order for the returned rows
	}
	
	public boolean updateTimeLapseById(int _id, String[] columns, String[] values){
		//public int update(Uri uri, ContentValues values, String selection,
		//		String[] selectionArgs) {		
		ContentValues contentValues = new ContentValues();
		for(int x=0;x<columns.length;x++){
			contentValues.put(columns[x], values[x]);
		}
		
		Date now = new Date();
		contentValues.put(SQLiteWrapper.COLUMN_MODIFIED_DATE, now.toString());
		
		//make sure timelapse-id is included
		contentValues.put(SQLiteWrapper.COLUMN_ID, _id);
		
		return updateOrInsertTimeLapseByContentValues(contentValues);
	}
	

	/**
	 * Attempt to update a TimeLapse record in the TimeLapseContentProvider.
	 * Failing this, inserts a new record.
	 * @param cv ContentValues representing a timelapse object
	 * @return true if an existing row was updated, false if a new row was created
	 */
	public boolean updateOrInsertTimeLapseByContentValues(ContentValues cv){
		
		String[] selectionArgs = {cv.getAsString(SQLiteWrapper.COLUMN_ID)};
		//Cursor testQuery = getContentResolver().query(TimeLapseContentProvider.CONTENT_URI, null, selectionClause, selectionArgs, null);
		//Log.d("QueryTest-getCount", String.valueOf(testQuery.getCount()));
		//Log.d("QueryTest-ID", String.valueOf(testQuery.getString(testQuery.getColumnIndex(SQLiteWrapper.COLUMN_TIMELAPSE_ID))));
		
		int numUpdated = getContentResolver().update(
				TimeLapseContentProvider.CONTENT_URI, 
				cv, 
				selectionClause, 
				selectionArgs);
		
		if(numUpdated > 0)
			return true;
		else{
			Log.d("updateOrInsertTimeLapseByContentValue","kindly note that this behavior is currently fucked");
			//TODO: Add defaults for not null fields
			getContentResolver().insert(TimeLapseContentProvider.CONTENT_URI, cv);
			return false;
		}
		
	}
	
	/**
	 * Given parallel arrays of columns and values, create a timelapse
	 * in the TimeLapseContentProvider, as well as on the External Filesystem
	 * @param columns
	 * @param values
	 * @return
	 */
	public Uri createTimeLapse(String[] columns, String[] values){
		ContentValues contentValues = new ContentValues();
		for(int x=0;x<columns.length;x++){
			contentValues.put(columns[x], values[x]);
		}
		// Determine next timelapse_id
		int next_timelapse_id = 1;
		Cursor cursor = getContentResolver().query(TimeLapseContentProvider.CONTENT_URI, new String[] {SQLiteWrapper.COLUMN_TIMELAPSE_ID}, null, null, SQLiteWrapper.COLUMN_TIMELAPSE_ID + " DESC");
		if(cursor.moveToFirst()){
			next_timelapse_id = cursor.getInt(cursor.getColumnIndex(SQLiteWrapper.COLUMN_TIMELAPSE_ID)) + 1;
		}
		//File timelapse_dir = getOutputMediaDir(input[0].getAsInteger(SQLiteWrapper.COLUMN_TIMELAPSE_ID));
		//Log.d("Directory_path", "" + FileUtils.getOutputMediaDir(next_timelapse_id).getAbsolutePath());
		contentValues.put(SQLiteWrapper.COLUMN_DIRECTORY_PATH, FileUtils.getOutputMediaDir(next_timelapse_id).getAbsolutePath());
		
		Date now = new Date();
		contentValues.put(SQLiteWrapper.COLUMN_CREATION_DATE, now.toString());
		contentValues.put(SQLiteWrapper.COLUMN_MODIFIED_DATE, now.toString());
		
		contentValues.put(SQLiteWrapper.COLUMN_IMAGE_COUNT, 0);

		contentValues.put(SQLiteWrapper.COLUMN_TIMELAPSE_ID, String.valueOf(next_timelapse_id));
		
		
		
		// Save TimeLapse to filesystem
		new FileUtils.SaveTimeLapsesOnFilesystem().execute(contentValues);
		Log.d("TimeLapseCollision","Writing from CreateTimeLapse");
		return getContentResolver().insert(TimeLapseContentProvider.CONTENT_URI, contentValues);
	}
}
