/*
* Copyright (C) 2015-2017 Zebra Technologies Corp
* All rights reserved.
*/
package com.symbol.profileappmgrsample1;

import java.io.StringReader;
import java.sql.Timestamp;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.ProfileManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements EMDKListener {

    final String TAG = getClass().getSimpleName();

    //Assign the profile name used in EMDKConfig.xml
    private String _profileName = "ExecuteAppMgrProfile";

    private String _packageName = "";

    //Declare a variable to store ProfileManager object
    private ProfileManager profileManager = null;

    //Declare a variable to store EMDKManager object
    private EMDKManager emdkManager = null;

    private TextView statusTextView = null;


    // Provides the error type for characteristic-error
    private String errorType = "";

    // Provides the parm name for parm-error
    private String parmName = "";

    // Provides error description
    private String errorDescription = "";

    // Provides error string with type/name + description
    private String errorString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusTextView = (TextView) findViewById(R.id.textViewStatus);

        //The EMDKManager object will be created and returned in the callback.
        EMDKResults results = EMDKManager.getEMDKManager(getApplicationContext(), this);

        //Check the return status of EMDKManager object creation.
        if(results.statusCode == EMDKResults.STATUS_CODE.SUCCESS) {
            //EMDKManager object creation success
        }else {
            //EMDKManager object creation failed
        }
    }

    public void onButtonClick(View v) {

        _packageName = ((EditText) findViewById(R.id.editTextName)).getText().toString().trim().toLowerCase();

        if (_packageName.equals("")) {
            Toast.makeText(this, "Must specify a valid package name.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.i(TAG, "Package name is: " + _packageName);

        switch (v.getId()) {
            case R.id.buttonEnable:
                executeProfile("EnableApplication", _packageName);
                break;
            case R.id.buttonDisable:
                executeProfile("DisableApplication", _packageName);
                break;
        }

    }

    private void executeProfile(String action, String packageName) {

        String[] profileXml = new String[1];

        profileXml[0] = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <characteristic type=\"Profile\">\n" +
                "    <parm name=\"ProfileName\" value=\"ExecuteAppMgrProfile\"/>\n" +
                "    <parm name=\"ModifiedDate\" value=\"2018-06-06 17:33:05\"/>\n" +
                "    <parm name=\"TargetSystemVersion\" value=\"6.0\"/>\n" +
                "    <characteristic type=\"AppMgr\" version=\"5.1\">\n" +
                "      <parm name=\"emdk_name\" value=\"\"/>\n" +
                "      <parm name=\"Action\" value=\"" + action + "\"/>\n" +
                "      <parm name=\"Package\" value=\"" + packageName + "\"/>\n" +
                "    </characteristic>\n" +
                "  </characteristic>";

        new ProcessProfileTask().execute(profileXml[0]);

    }


    @Override
    protected void onDestroy() {

        super.onDestroy();

        //Clean up the objects created by EMDK manager
        if (profileManager != null)
            profileManager = null;

        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;
        }
    }

    @Override
    public void onClosed() {

        //This callback will be issued when the EMDK closes unexpectedly.
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;
        }

        statusTextView.setText("Status: " + "EMDK closed unexpectedly! Please close and restart the application.");
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {

        //This callback will be issued when the EMDK is ready to use.
        statusTextView.setText("EMDK is open and ready.");

        this.emdkManager = emdkManager;

        //Get the ProfileManager object to process the profiles
        profileManager = (ProfileManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.PROFILE);
    }

    // Method to parse the XML response using XML Pull Parser
    public void parseXML(XmlPullParser myParser) {
        int event;
        try {
            // Retrieve error details if parm-error/characteristic-error in the response XML
            event = myParser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = myParser.getName();
                switch (event) {
                    case XmlPullParser.START_TAG:

                        if (name.equals("parm-error")) {
                            parmName = myParser.getAttributeValue(null, "name");
                            errorDescription = myParser.getAttributeValue(null, "desc");
                            errorString = " (Name: " + parmName + ", Error Description: " + errorDescription + ")";
                            return;
                        }

                        if (name.equals("characteristic-error")) {
                            errorType = myParser.getAttributeValue(null, "type");
                            errorDescription = myParser.getAttributeValue(null, "desc");
                            errorString = " (Type: " + errorType + ", Error Description: " + errorDescription + ")";
                            return;
                        }
                        break;
                    case XmlPullParser.END_TAG:

                        break;
                }
                event = myParser.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private class ProcessProfileTask extends AsyncTask<String, Void, EMDKResults> {

        @Override
        protected EMDKResults doInBackground(String... params) {

            //Call process profile to modify the profile of specified profile name
            EMDKResults results = profileManager.processProfile(_profileName, ProfileManager.PROFILE_FLAG.SET, params);

            return results;
        }

        @Override
        protected void onPostExecute(EMDKResults results) {

            super.onPostExecute(results);

            String resultString = "";

            //Check the return status of processProfile
            if(results.statusCode == EMDKResults.STATUS_CODE.CHECK_XML) {

                // Get XML response as a String
                String statusXMLResponse = results.getStatusString();
                Log.d(TAG, "statusXmlResponse: " + statusXMLResponse);

                try {
                    // Create instance of XML Pull Parser to parse the response
                    XmlPullParser parser = Xml.newPullParser();
                    // Provide the string response to the String Reader that reads
                    // for the parser
                    parser.setInput(new StringReader(statusXMLResponse));
                    // Call method to parse the response
                    parseXML(parser);

                    if ( TextUtils.isEmpty(parmName) && TextUtils.isEmpty(errorType) && TextUtils.isEmpty(errorDescription) ) {

                        resultString = "Profile update success: " + TimestampUtils.getISO8601StringForCurrentDate();
                    }
                    else {

                        resultString = "Profile update failed." + errorString + " " + TimestampUtils.getISO8601StringForCurrentDate();
                    }

                } catch (XmlPullParserException e) {
                    resultString =  e.getMessage();
                }
            }

            statusTextView.setText(resultString);
        }
    }

}
