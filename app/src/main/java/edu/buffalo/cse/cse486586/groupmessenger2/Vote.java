package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;

/**
 * GroupMessenger
 * <p/>
 * Created by darrenxyli on 3/10/15.
 * Changed by darrenxyli on 3/10/15 6:10 PM.
 */
public class Vote implements Serializable {

    private static final long serialVersionUID = 6393191541861509332L;

    public int voteProcess;
    public long voteSeqNum;

    public Vote(int p, long s) {
        voteProcess = p;
        voteSeqNum = s;
    }
}
