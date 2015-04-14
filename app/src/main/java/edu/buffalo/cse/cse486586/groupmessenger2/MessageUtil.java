package edu.buffalo.cse.cse486586.groupmessenger2;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * MessageUtil

 * Created by darrenxyli on 3/5/15.
 * Changed by darrenxyli on 3/5/15 9:24 PM.
 */
public class MessageUtil {
    public int DEVICEPORT;
    public ArrayList<Integer> REMOTEPORTS= new ArrayList<>(Arrays.asList(11108,11112,11116,11120,11124));
    public static final int SERVER_PORT = 10000;
    public static final String DATABASE_AUTHORITY = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
    public static final String DATABASE_SCHEME = "content";
    public static final Uri DATABASE_CONTENT_URL = MessageUtil.buildUri(DATABASE_SCHEME, DATABASE_AUTHORITY);
    public static final int TIMEOUT = 5000;

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme String
     * @param authority String
     * @return the URI
     */
    public static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public MessageUtil(int port) {
        DEVICEPORT = port;
    }

}
